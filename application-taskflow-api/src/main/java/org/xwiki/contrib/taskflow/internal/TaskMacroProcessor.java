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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.taskflow.TaskFlowManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.block.MacroBlock;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Component responsible for processing "checktask" macros embedded in XWiki documents.
 * <p>
 * This processor extracts macro parameters, synchronizes them with task objects in the document,
 * and optionally triggers notifications to responsible users.
 * </p>
 *
 * <p>
 * Registered as a singleton component with role {@code TaskMacroProcessor}, allowing it to be injected
 * wherever macro processing is needed.
 * </p>
 *
 *  @version $Id$
 *  @since 2.0
 */
@Component(roles = TaskMacroProcessor.class)
@Singleton
public class TaskMacroProcessor
{
    private static final String REMINDER_TIMES = "reminderTimes";

    private static final String RID = "rid";

    private static final String RESPONSIBLE = "responsible";

    private static final String DUE_DATE = "dueDate";

    private static final String SEPARATOR = ",";

    @Inject
    private TaskFlowManager taskFlowManager;

    @Inject
    private DocumentReferenceResolver<String> resolver;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private Logger logger;

    /**
     * Processes a single "checktask" macro block within an XWiki document.
     * <p>
     * This method extracts task metadata from the macro, creates or updates the corresponding task object,
     * and tracks the task's RID. If the task is newly created, it sends notifications to the responsible users.
     * </p>
     *
     * @param macro the macro block containing task parameters and content
     * @param doc the XWiki document where the macro resides
     * @param taskClassRef reference to the task class used for storing task objects
     * @param foundRids a set used to collect all RIDs found during processing
     * @param simpleDateFormat the date format used to parse the macro's due date
     * @param context the current XWiki execution context
     * @throws XWikiException if an error occurs while accessing or modifying the document
     */
    public void processMacro(MacroBlock macro, XWikiDocument doc, DocumentReference taskClassRef,
        Set<String> foundRids, SimpleDateFormat simpleDateFormat, XWikiContext context) throws XWikiException
    {
        Map<String, String> params = new HashMap<>(macro.getParameters());
        String rid = resolveRID(macro, params);
        foundRids.add(rid);

        String taskCreator = serializer.serialize(context.getUserReference());
        BaseObject taskObj = doc.getXObject(taskClassRef, RID, rid);
        boolean sendNotification = taskObj == null;

        taskObj = getOrCreateTaskObject(doc, taskClassRef, rid, taskCreator, context);

        Date macroDueDate = parseDueDate(params.getOrDefault(DUE_DATE, ""), simpleDateFormat);
        String taskContent = macro.getContent();
        String responsible = params.getOrDefault(RESPONSIBLE, "");
        List<DocumentReference> responsibleUsers = Arrays
            .stream(responsible.split(SEPARATOR))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(resolver::resolve)
            .collect(Collectors.toList());

        taskObj.setDateValue(DUE_DATE, macroDueDate);
        taskObj.setStringListValue(REMINDER_TIMES, List.of(params.getOrDefault(REMINDER_TIMES, "").split(SEPARATOR)));
        taskObj.setStringValue("task", macro.getContent());
        taskObj.setLargeStringValue(RESPONSIBLE, responsible);

        if (sendNotification) {
            notifyUsers(doc, rid, taskContent, taskCreator, responsibleUsers, context);
        }
    }

    private String resolveRID(MacroBlock macro, Map<String, String> params)
    {
        String rid = params.get(RID);
        if (StringUtils.isBlank(rid)) {
            rid = taskFlowManager.generateRID();
            macro.setParameter(RID, rid);
        }
        return rid;
    }

    private BaseObject getOrCreateTaskObject(XWikiDocument doc, DocumentReference taskClassRef,
        String rid, String taskCreator, XWikiContext context) throws XWikiException
    {
        BaseObject taskObj = doc.getXObject(taskClassRef, RID, rid);
        if (taskObj == null) {
            taskObj = doc.newXObject(taskClassRef, context);
            taskObj.setStringValue(RID, rid);
            taskObj.setIntValue("done", 0);
            taskObj.setLargeStringValue("creator", taskCreator);
        }
        return taskObj;
    }

    private Date parseDueDate(String dateStr, SimpleDateFormat format)
    {
        if (StringUtils.isBlank(dateStr)) {
            return null;
        }
        try {
            return format.parse(dateStr);
        } catch (ParseException e) {
            logger.warn("Cannot parse the macro dueDate '{}'", dateStr, e);
            return null;
        }
    }

    private void notifyUsers(XWikiDocument doc, String rid, String taskContent,
        String taskCreator, List<DocumentReference> users, XWikiContext context)
    {
        String taskUrl = doc.getExternalURL("view", context) + "#" + rid;
        for (DocumentReference user : users) {
            Map<String, String> taskEventParams = Map.of(
                "taskContent", taskContent,
                "taskCreator", taskCreator,
                "taskUrl", taskUrl
            );
            taskFlowManager.notifyResponsibleUser(doc.getDocumentReference(), user, "assigned", taskEventParams);
        }
    }
}
