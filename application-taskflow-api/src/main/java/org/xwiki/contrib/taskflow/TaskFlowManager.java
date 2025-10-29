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
package org.xwiki.contrib.taskflow;

import java.util.List;
import java.util.Map;

import org.xwiki.component.annotation.Role;
import org.xwiki.model.reference.DocumentReference;

/**
 * Task flow manager allowing to handle operations on tasks.
 *
 * @version $Id$
 * @since 2.0
 */
@Role
public interface TaskFlowManager
{
    /**
     * Retrieves a list of tasks that are due for reminder notifications.
     *
     * @return a map representing tasks to be reminded by hour intervals.
     */
    Map<String, Map<DocumentReference, Map<DocumentReference, List<String>>>> getTasksToRemind();

    /**
     * Sends a notification to a responsible user about a specific task.
     *
     * @param taskRef the reference to the task document that requires attention
     * @param userRef the reference to the user who is responsible for the task
     * @param eventType the type of event
     * @param taskEventParams extra parameters of the event
     *
     */
    void notifyResponsibleUser(DocumentReference taskRef, DocumentReference userRef, String eventType, Map<String,
        String> taskEventParams);

    /**
     * Generates a unique identifier (RID) for a task or object.
     * <p>
     * The returned RID is typically used to distinguish individual items within a document or system.
     *
     * @return a newly generated unique string identifier
     */
    String generateRID();
}
