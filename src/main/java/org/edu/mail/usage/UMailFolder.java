package org.edu.mail.usage;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用邮件文件夹
 */
public enum UMailFolder {
    RECEIVE_BOX("收件箱", "INBOX"),
    DRAFT_BOX("草稿箱", "草稿箱"),
    SENT_BOX("已发送", "已发送"),
    TRASH_BOX("垃圾箱", "垃圾箱"),
    DELETED_BOX("已删除", "已删除");

    UMailFolder(String label, String value){
        this.label = label;
        this.value = value;
    }

    private String label;
    private String value;

    public String getLabel(){
        return this.label;
    }
    public String getValue(){
        return this.value;
    }
    public static List toLabelList() {
        List<String> list = new ArrayList<>();
        for (UMailFolder f: values()){
            list.add(f.label);
        }
        return list;
    }
    public static List toValueList() {
        List<String> list = new ArrayList<>();
        for (UMailFolder f: values()){
            list.add(f.value);
        }
        return list;
    }
}
