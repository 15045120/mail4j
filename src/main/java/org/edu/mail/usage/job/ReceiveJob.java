package org.edu.mail.usage.job;

import org.quartz.Job;
import org.quartz.JobExecutionContext;

import org.edu.mail.usage.UMailException;
import org.edu.mail.usage.UMailReceiver;
/**
 * 定时接收工作类
 * @author ibu
 */
public class ReceiveJob  implements Job {
    public ReceiveJob(){
    }
    @Override
    public void execute(JobExecutionContext context) {
        UMailReceiver receiver = (UMailReceiver) context.getJobDetail().getJobDataMap().get("receiver");
        String uid = (String) context.getJobDetail().getJobDataMap().get("uid");
        try {
            receiver.doSync(uid);
        }catch (UMailException e){
            e.printStackTrace();
        }
        System.err.println("ReceiveJob Executed");
    }
}
