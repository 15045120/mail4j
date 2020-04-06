package org.edu.mail.usage.job;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import java.util.Map;

import org.edu.mail.usage.UMailException;
import org.edu.mail.usage.UMailMessage;
import org.edu.mail.usage.UMailSender;


/**
 * 定时发送工作类
 * @author ibu
 */
public class SendJob implements Job {
    public SendJob(){
    }
    @Override
    public void execute(JobExecutionContext context) {
        UMailSender sender = (UMailSender) context.getJobDetail().getJobDataMap().get("sender");
        UMailMessage sourceMsg = (UMailMessage) context.getJobDetail().getJobDataMap().get("sourceMsg");
        int sendWay = (int) context.getJobDetail().getJobDataMap().get("sendWay");
        Map args = (Map) context.getJobDetail().getJobDataMap().get("args");
        try {
            sender.doSend(sourceMsg, sendWay, args);
        }catch (UMailException e){
            e.printStackTrace();
        }
        System.err.println("SendJob Executed");
    }
}