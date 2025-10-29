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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.taskflow.notifications.events.TaskFlowAssignedEvent;
import org.xwiki.eventstream.Event;
import org.xwiki.eventstream.RecordableEvent;
import org.xwiki.eventstream.RecordableEventConverter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * Add additional information to {@link TaskFlowAssignedEvent}.
 *
 * @version $Id$
 * @since 2.0
 */
@Singleton
@Named(TaskFlowEventConverter.NAME)
@Component
public class TaskFlowEventConverter implements RecordableEventConverter
{
    /**
     * The name of this component.
     */
    public static final String NAME = "TaskFlowEventConverter";

    @Inject
    private RecordableEventConverter defaultConverter;

    @Inject
    private Logger logger;

    @Override
    public Event convert(RecordableEvent recordableEvent, String source, Object data) throws Exception
    {
        TaskFlowAssignedEvent taskEvent = (TaskFlowAssignedEvent) recordableEvent;
        Map<String, Object> taskEventExtraParams = new HashMap<>();
        taskEventExtraParams.put("taskContent", taskEvent.getTaskContent());
        taskEventExtraParams.put("taskCreator", taskEvent.getTaskCreator());
        taskEventExtraParams.put("taskUrl", taskEvent.getTaskUrl());

        Event convertedEvent = defaultConverter.convert(recordableEvent, source, data);

        convertedEvent.setBody(serializeParams(taskEventExtraParams));

        return convertedEvent;
    }

    @Override
    public List<RecordableEvent> getSupportedEvents()
    {
        return List.of(new TaskFlowAssignedEvent());
    }

    /**
     * Utility method to convert an object to JSON.
     *
     * @param params the localization parameters to serialize.
     */
    private String serializeParams(Map<String, Object> params)
    {
        String json = null;
        try {
            ObjectWriter ow = new ObjectMapper().writer();
            json = ow.writeValueAsString(params);
        } catch (Exception e) {
            logger.warn("Error while serializing parameters of TaskChangedEvent:", e);
        }
        return json;
    }
}
