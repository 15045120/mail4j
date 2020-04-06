package org.edu.mail.usage.search;

/**
 * lucene索引项
 */
public interface IndexTerm {
     String BOX = "box";
     String NEW = "new";

     String MESSAGE_UID = "uid";
     String DATE = "date";
     String FROM = "from";
     String TO = "to";
     String CC = "cc";
     String BCC = "bcc";
     String SUBJECT = "subject";
     String MESSAGE_ID = "id";
     String CONTENT = "content";
     String FLAGS = "flag";
     String ATTACHMENTS = "attachment";
}
