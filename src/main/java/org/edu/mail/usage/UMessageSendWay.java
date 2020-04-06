package org.edu.mail.usage;

/**
 * 发送方式
 */
public interface UMessageSendWay {
    int NORMAL           = 0x00000001;  // 正常发送
    int GROUP_NOT_SEE    = 0x00000002;  // 群发单显
    int SCHEDULE         = 0x00000004;  // 定时发送
    int ENCRYPT          = 0x00000008;  // 加密发送
}
