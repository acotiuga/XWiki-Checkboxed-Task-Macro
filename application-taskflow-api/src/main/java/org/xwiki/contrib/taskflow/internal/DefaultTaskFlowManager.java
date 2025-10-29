/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.taskflow.internal;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.taskflow.TaskFlowManager;
import org.xwiki.contrib.taskflow.notifications.events.TaskFlowAssignedEvent;
import org.xwiki.contrib.taskflow.notifications.events.TaskFlowExpiringEvent;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.observation.ObservationManager;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryFilter;
import org.xwiki.query.QueryManager;
import org.xwiki.text.StringUtils;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Default implementation of the {@link TaskFlowManager} role.
 *
 * @version $Id$
 * @since 2.0
 */
@Component
@Singleton
public class DefaultTaskFlowManager implements TaskFlowManager
{
    /**
     * Default event source.
     */
    private static final String EVENT_SOURCE = "org.xwiki.contrib:application-taskFlow-api";

    private static final String TASK_CLASS_NAME = "Macros.CheckboxedTask.Code.TaskClass";

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private Logger logger;

    @Inject
    private ObservationManager observationManager;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    @Named("document")
    private QueryFilter documentQueryFilter;

    @Inject
    @Named("unique")
    private QueryFilter uniqueQueryFilter;

    @Inject
    private QueryManager queryManager;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    /**
     * Builds a nested data model representing tasks that are due within specific reminder intervals.
     * <p>
     * The returned structure maps each interval code (e.g., "h1", "h4") to a set of documents, each containing task
     * identifiers and their associated responsible users.
     * <p>
     * Example initial output: interval → document → task ID → list of responsible users.
     * <pre>
     * {
     *   "h1": {
     *     Main.WebHome: {
     *       "ql4-1761153864688": [
     *         "XWiki.testUser",
     *         "XWiki.Admin"
     *       ]
     *     }
     *   }
     * }
     * </pre>
     * Becomes: interval → user → document → list of task IDs.
     *
     * @return a map that represents a transformation of the initial output map.
     */
    @Override
    public Map<String, Map<DocumentReference, Map<DocumentReference, List<String>>>> getTasksToRemind()
    {
        Map<String, Long> intervals = Map.of(
            "h1", 1L,
            "h2", 2L,
            "h4", 4L,
            "h8", 8L,
            "h12", 12L,
            "d1", 24L,
            "d2", 48L,
            "d5", 120L
        );

        XWikiContext context = xcontextProvider.get();
        XWiki xwiki = context.getWiki();
        DocumentReference taskClassRef = resolver.resolve(TASK_CLASS_NAME);

        Map<String, Map<DocumentReference, Map<String, List<DocumentReference>>>> tasksToRemindMap = new HashMap<>();
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());

        for (Map.Entry<String, Long> entry : intervals.entrySet()) {
            long intervalHours = entry.getValue();

            ZonedDateTime startHours = now.plusHours(intervalHours);
            ZonedDateTime endHours = startHours.plusHours(1);
            Date startHoursDate = Date.from(startHours.toInstant());
            Date endHoursDate = Date.from(endHours.toInstant());

            try {
                List<DocumentReference> taskRefs = getTaskReferences(startHoursDate, endHoursDate);

                Map<DocumentReference, Map<String, List<DocumentReference>>> taskRefMap = new HashMap<>();

                for (DocumentReference taskRef : taskRefs) {
                    XWikiDocument taskDoc = xwiki.getDocument(taskRef, context);
                    Map<String, List<DocumentReference>> taskDetailsMap =
                        getTaskDetailsMap(taskDoc, taskClassRef, entry.getKey(), startHoursDate, endHoursDate);

                    if (!taskDetailsMap.isEmpty()) {
                        taskRefMap.put(taskRef, taskDetailsMap);
                    }
                }
                if (!taskRefMap.isEmpty()) {
                    tasksToRemindMap.put(entry.getKey(), taskRefMap);
                }
            } catch (QueryException e) {
                logger.error("Failed to get due tasks", e);
                return Collections.emptyMap();
            } catch (XWikiException e) {
                throw new RuntimeException(e);
            }
        }
        return invertTasksToRemind(tasksToRemindMap);
    }

    @Override
    public void notifyResponsibleUser(DocumentReference taskRef, DocumentReference userRef, String eventType,
        Map<String, String> taskEventParams)
    {
        XWikiContext context = xcontextProvider.get();
        try {
            XWikiDocument taskDoc = context.getWiki().getDocument(taskRef, context);
            Set<String> target = new HashSet<>();
            target.add(serializer.serialize(userRef));

            if (eventType.equals("expiring")) {
                observationManager.notify(new TaskFlowExpiringEvent(target, taskEventParams), EVENT_SOURCE, taskDoc);
            } else {
                observationManager.notify(new TaskFlowAssignedEvent(target, taskEventParams), EVENT_SOURCE, taskDoc);
            }
        } catch (XWikiException e) {
            logger.error(
                String.format("An error appeared when notifying responsible user of the document [%s].", taskRef), e);
        }
    }

    @Override
    public String generateRID()
    {
        String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder prefix = new StringBuilder(3);
        for (int i = 0; i < 3; i++) {
            int index = random.nextInt(alphabet.length());
            prefix.append(alphabet.charAt(index));
        }
        return prefix.append("-").append(System.currentTimeMillis()).toString();
    }

    private List<DocumentReference> getTaskReferences(Date start, Date end) throws QueryException
    {
        String statement = String.format(
            "from doc.object(%s) task where task.dueDate >= :startHoursDate and task.dueDate < :endHoursDate",
            TASK_CLASS_NAME);
        Query query = queryManager.createQuery(statement, Query.XWQL);
        query.bindValue("startHoursDate", start).bindValue("endHoursDate", end);
        query.addFilter(documentQueryFilter).addFilter(uniqueQueryFilter);
        return query.execute();
    }

    private Map<String, List<DocumentReference>> getTaskDetailsMap(XWikiDocument taskDoc,
        DocumentReference taskClassRef, String intervalKey, Date startDate, Date endDate)
    {
        Map<String, List<DocumentReference>> taskDetailsMap = new HashMap<>();
        List<BaseObject> taskObjs = taskDoc.getXObjects(taskClassRef);

        for (BaseObject taskObj : taskObjs) {
            String usernames = taskObj.getLargeStringValue("responsible");
            List<String> reminderTimes = taskObj.getListValue("reminderTimes");
            Date dueDate = taskObj.getDateValue("dueDate");

            if (StringUtils.isBlank(usernames) || dueDate == null || !reminderTimes.contains(intervalKey)) {
                continue;
            }

            if (dueDate.compareTo(startDate) >= 0 && dueDate.compareTo(endDate) < 0) {
                List<DocumentReference> responsibleUsers = Arrays.stream(usernames.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(resolver::resolve)
                    .collect(Collectors.toList());

                taskDetailsMap.put(taskObj.getStringValue("rid"), responsibleUsers);
            }
        }

        return taskDetailsMap;
    }

    /**
     * Transforms a nested task reminder map into a user-centric structure for easier notification dispatching.
     * <p>
     * The input map is organized by interval → document → task ID → responsible users. This method inverts that
     * structure to group tasks by responsible user instead.
     * <p>
     * The resulting data model has the following shape:
     * <pre>
     * {
     *   "h1": {
     *     XWiki.testUser: {
     *       Main.WebHome: [
     *         "ql4-1761153864688"
     *       ]
     *     },
     *     XWiki.Admin: {
     *       Main.WebHome: [
     *         "ql4-1761153864688"
     *       ]
     *     }
     *   }
     * }
     * </pre>
     *
     * @param originalMap the original task map structured as: interval → document → task ID → list of responsible
     *     users
     * @return a transformed map structured as: interval → user → document → list of task IDs
     */
    private Map<String, Map<DocumentReference, Map<DocumentReference, List<String>>>> invertTasksToRemind(
        Map<String, Map<DocumentReference, Map<String, List<DocumentReference>>>> originalMap)
    {

        Map<String, Map<DocumentReference, Map<DocumentReference, List<String>>>> invertedMap = new HashMap<>();

        for (Map.Entry<String, Map<DocumentReference,
            Map<String, List<DocumentReference>>>> intervalEntry : originalMap.entrySet()) {
            String interval = intervalEntry.getKey();
            Map<DocumentReference, Map<DocumentReference, List<String>>> userMap = new HashMap<>();

            for (Map.Entry<DocumentReference, Map<String, List<DocumentReference>>> docEntry : intervalEntry.getValue()
                .entrySet()) {
                DocumentReference docRef = docEntry.getKey();

                for (Map.Entry<String, List<DocumentReference>> taskEntry : docEntry.getValue().entrySet()) {
                    String taskId = taskEntry.getKey();
                    List<DocumentReference> users = taskEntry.getValue();

                    for (DocumentReference userRef : users) {
                        userMap
                            .computeIfAbsent(userRef, k -> new HashMap<>())
                            .computeIfAbsent(docRef, k -> new ArrayList<>())
                            .add(taskId);
                    }
                }
            }

            invertedMap.put(interval, userMap);
        }

        return invertedMap;
    }
}
