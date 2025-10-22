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
package org.xwiki.contrib.taskflow.internal.listener;

import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.bridge.event.DocumentCreatingEvent;
import org.xwiki.bridge.event.DocumentUpdatingEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.observation.EventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.MacroBlockMatcher;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Listens to document creating and updating events to add task objects to XWiki documents.
 *
 * @version $Id$
 * @since 2.0
 */
@Component
@Named(TaskListener.NAME)
@Singleton
public class TaskListener implements EventListener
{
    public static final String REMINDER_TIMES = "reminderTimes";

    protected static final String NAME = "TaskListener";

    private static final String TASK = "task";

    private static final String RID = "rid";

    private static final List<Event> EVENTS = List.of(new DocumentCreatingEvent(), new DocumentUpdatingEvent());

    private static final String RESPONSIBLE = "responsible";

    private static final String DUE_DATE = "dueDate";

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private Logger logger;

    @Inject
    @Named("xwiki/2.1")
    private Parser parser;

    @Inject
    @Named("xwiki/2.1")
    private BlockRenderer blockRenderer;

    @Override
    public List<Event> getEvents()
    {
        return EVENTS;
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        XWikiDocument currentDoc = (XWikiDocument) source;

        if (currentDoc.getDocumentReference().equals(resolver.resolve("Macros.CheckboxedTask.WebHome"))) {
            return;
        }

        // If it’s an update but the content has not changed (e.g. metadata edit, comment added), skip parsing entirely.
        if (!currentDoc.isContentDirty()) {
            return;
        }

        XWikiContext context = (XWikiContext) data;
        DocumentReference taskClassRef = resolver.resolve("Macros.CheckboxedTask.Code.TaskClass");

        String content = currentDoc.getContent().trim();

        try {
            if (!content.contains("{{checktask")) {
                // No checktask macros at all, just clean objects if any exist
                if (!currentDoc.getXObjects(taskClassRef).isEmpty()) {
                    removeAllTasks(currentDoc, taskClassRef);
                }
            } else {
                synchronizeTasks(currentDoc, taskClassRef, context);
            }
        } catch (Exception e) {
            logger.error("Failed to synchronize tasks for [{}]", currentDoc.getDocumentReference(), e);
        }
    }

    private void synchronizeTasks(XWikiDocument doc, DocumentReference taskClassRef,
        XWikiContext context) throws Exception
    {
        String content = doc.getContent();
        XDOM xdom = parser.parse(new StringReader(content));
        List<MacroBlock> macros = xdom.getBlocks(new MacroBlockMatcher("checktask"), Block.Axes.DESCENDANT);

        Set<String> foundRids = new HashSet<>();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");

        for (MacroBlock macro : macros) {
            processMacro(macro, doc, taskClassRef, foundRids, simpleDateFormat, context);
        }
        removeStaleTasks(doc, taskClassRef, foundRids);

        // Re-serialize the XDOM back to wiki syntax to update macros content with rid.
        WikiPrinter wikiPrinter = new DefaultWikiPrinter();
        blockRenderer.render(xdom, wikiPrinter);
        doc.setContent(wikiPrinter.toString());
    }

    private void processMacro(MacroBlock macro, XWikiDocument doc, DocumentReference taskClassRef,
        Set<String> foundRids, SimpleDateFormat simpleDateFormat, XWikiContext context) throws XWikiException
    {
        Map<String, String> params = new HashMap<>(macro.getParameters());

        String rid = params.get(RID);
        if (StringUtils.isBlank(rid)) {
            rid = generateRID();
            macro.setParameter(RID, rid);
        }
        foundRids.add(rid);

        BaseObject taskObj = doc.getXObject(taskClassRef, RID, rid);
        if (taskObj == null) {
            taskObj = doc.newXObject(taskClassRef, context);
            taskObj.setStringValue(RID, rid);
            taskObj.setIntValue("done", 1);
            taskObj.setLargeStringValue("creator", serializer.serialize(context.getUserReference()));
            taskObj.setStringListValue(REMINDER_TIMES,
                List.of(params.getOrDefault(REMINDER_TIMES, "").split(",")));
        }

        String task = macro.getContent().trim();
        String responsible = params.getOrDefault(RESPONSIBLE, "");
        String macroDueDateStr = params.getOrDefault(DUE_DATE, "");
        Date macroDueDate = null;
        if (StringUtils.isNotBlank(macroDueDateStr)) {
            try {
                macroDueDate = simpleDateFormat.parse(macroDueDateStr);
            } catch (ParseException e) {
                logger.warn("Cannot parse the macro dueDate '{}'", macroDueDateStr, e);
            }
        }

        if (!Objects.equals(taskObj.getStringValue(TASK), task)
            || !Objects.equals(taskObj.getStringValue(RESPONSIBLE), responsible)
            || !Objects.equals(taskObj.getDateValue(DUE_DATE), macroDueDate))
        {
            taskObj.setStringValue(TASK, task);
            taskObj.setLargeStringValue(RESPONSIBLE, responsible);
            taskObj.setDateValue(DUE_DATE, macroDueDate);
        }
    }

    private void removeAllTasks(XWikiDocument doc, DocumentReference taskClassRef)
    {
        for (BaseObject obj : doc.getXObjects(taskClassRef)) {
            doc.removeXObject(obj);
        }
    }

    private void removeStaleTasks(XWikiDocument doc, DocumentReference taskClassRef, Set<String> validRids)
    {
        for (BaseObject obj : doc.getXObjects(taskClassRef)) {
            if (obj != null) {
                String rid = obj.getStringValue(RID);
                if (rid == null || !validRids.contains(rid)) {
                    doc.removeXObject(obj);
                }
            }
        }
    }

    private String generateRID()
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
}
