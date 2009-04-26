/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.sakaiproject.kernel.messaging;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.sakaiproject.kernel.api.jcr.JCRService;
import org.sakaiproject.kernel.api.jcr.support.JcrUtils;
import org.sakaiproject.kernel.api.locking.LockTimeoutException;
import org.sakaiproject.kernel.api.messaging.Message;
import org.sakaiproject.kernel.api.messaging.MessageHandler;
import org.sakaiproject.kernel.api.messaging.MessagingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;

/**
 * 
 * @scr.component description="Handler for internally delivered messages."
 * @scr.property name="type" value="internal"
 */
public class InternalMessageHandler implements MessageHandler {
  private static final String TYPE = Message.Type.INTERNAL.toString();
  private static final FastDateFormat dateStruct;
  private static final Logger LOGGER = LoggerFactory
      .getLogger(InternalMessageHandler.class);

  static {
    dateStruct = FastDateFormat.getInstance("yyyy/MM/");
  }

  // TODO: these need explicit setters to make the whole thing thread safe
  /** @scr.reference */
  private JCRService jcr;

  /** @scr.reference */
  private UserFactoryService userFactory;

  /**
   * Default constructor
   */
  public InternalMessageHandler() {
  }

  public void handle(String userID, String filePath, String fileName, Node node) {
    try {
      Session session = jcr.getSession();
      Workspace workspace = session.getWorkspace();

      Property toProp = node.getProperty(Message.Field.TO.toString());
      String toVal = toProp.getString();
      String[] rcpts = StringUtils.split(toVal, ",");

      if (rcpts != null) {
        for (String rcpt : rcpts) {
          String msgPath = buildMessagesPath(rcpt) + fileName;
          LOGGER.debug("Writing {0} to {1}", filePath, msgPath);
          workspace.copy(filePath, msgPath);
          Node n = (Node) session.getItem(msgPath);
          JcrUtils.addNodeLabel(jcr, n, "inbox");
        }
      }

      // move the original node to the common message store for the sender and
      // label it as "sent". create the parent if it doesn't exist.
      Property fromProp = node.getProperty(Message.Field.FROM.toString());
      String from = fromProp.getString();

      String sentPath = buildMessagesPath(from);
      // if (!session.itemExists(sentPath)) {
      // Node root = session.getRootNode();
      // Node targetNode = root.addNode(sentPath);
      // // the node *must* be saved to make it available to the move.
      // // call to the parent-parent so that "messages" is used as the saving
      // // node rather than the parent (year) as it may not exist yet.
      // targetNode.getParent().getParent().save();
      // }
      String sentMsgPath = sentPath + "/" + fileName;
      LOGGER.debug("Moving message {0} to {1} ", filePath, sentMsgPath);
      workspace.move(filePath, sentMsgPath);
      Node movedNode = (Node) session.getItem(sentMsgPath);
      JcrUtils.addNodeLabel(jcr, movedNode, "sent");
      node.getParent().save();
    } catch (RepositoryException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (LockTimeoutException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  public String getType() {
    return TYPE;
  }

  private String buildMessagesPath(String user) {
    Date now = new Date();
    String msgStructPath = dateStruct.format(now);
    String path = userFactory.getUserPrivatePath(user);
    path += MessagingConstants.FOLDER_MESSAGES;
    path += msgStructPath;
    return path;
  }
}