package org.edu.mail.usage.search;

import org.edu.mail.usage.UMailConfigure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Created by ibu on 2019/2/28.
 */
public abstract class LuceneService extends UMailConfigure {
    protected Logger logger = Logger.getLogger(LuceneService.class.getName());
    // 用户名
    protected String account;
    // 邮件存储路径
    Path storagePath;
    // 邮件索引路径
    Path indexPath;

    LuceneService(String uid, String account){
        this.account = account;
        storagePath = Paths.get(conf.getProperty("storage.directory")+uid+separator+account);
        indexPath = Paths.get(conf.getProperty("index.directory")+uid+separator+account);
        try {
            if (!Files.exists(indexPath)) {
                indexPath = Files.createDirectories(indexPath);
            }
        }catch (IOException e){
            e.printStackTrace();
        }
    }
}
