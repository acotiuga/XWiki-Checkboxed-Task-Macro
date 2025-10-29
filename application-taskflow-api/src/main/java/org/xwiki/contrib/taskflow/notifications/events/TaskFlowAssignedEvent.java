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
package org.xwiki.contrib.taskflow.notifications.events;

import java.util.Set;

import org.xwiki.eventstream.RecordableEvent;
import org.xwiki.eventstream.TargetableEvent;

/**
 * Task Assigned event. Notify responsible user about the assigned task.
 *
 * @version $Id$
 * @since 2.0
 */
public class TaskFlowAssignedEvent implements RecordableEvent, TargetableEvent
{
    private Set<String> target;

    private String taskContent;

    private String taskCreator;

    private String taskUrl;

    /**
     * The default non-arguments constructor.
     */
    public TaskFlowAssignedEvent()
    {
    }

    /**
     * Create a new instance with the given data.
     *
     * @param target the list of users targeted by the event.
     * @param taskContent the content of the task macro.
     * @param taskCreator the user that created the task.
     */
    public TaskFlowAssignedEvent(Set<String> target, String taskContent, String taskCreator, String taskUrl)
    {
        this.target = target;
        this.taskContent = taskContent;
        this.taskCreator = taskCreator;
        this.taskUrl = taskUrl;
    }

    @Override
    public Set<String> getTarget()
    {
        return this.target;
    }

    /**
     * @return the content of the task macro.
     */
    public String getTaskContent()
    {
        return this.taskContent;
    }

    /**
     * @return the user that created the task.
     */
    public String getTaskCreator()
    {
        return this.taskCreator;
    }

    /**
     * @return the user that created the task.
     */
    public String getTaskUrl()
    {
        return this.taskUrl;
    }

    @Override
    public boolean matches(Object otherEvent)
    {
        return otherEvent instanceof TaskFlowAssignedEvent;
    }
}
