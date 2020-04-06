package org.edu.mail.usage.search;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.lionsoul.jcseg.analyzer.JcsegAnalyzer;
import org.lionsoul.jcseg.tokenizer.core.JcsegTaskConfig;

import org.edu.mail.usage.UMailException;
import org.edu.mail.usage.util.UMailUtils;
import static org.edu.mail.usage.UMessageContentType.TEXT_HTML;
import static org.edu.mail.usage.UMessageContentType.TEXT_PLAIN;
import static org.edu.mail.usage.search.IndexTerm.*;
import static org.edu.mail.usage.search.IndexTerm.ATTACHMENTS;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;



/**
 * Created by ibu on 2019/2/28.
 */
public class LuceneIndexer extends LuceneService{
    private LuceneIndexer(String uid, String account){
        super(uid, account);
    }
    // 获取邮件接收对象
    public static LuceneIndexer newInstance(String uid, String account) throws UMailException {
        if(StringUtils.isEmpty(account)){
            throw new UMailException("账号不能为空", new NullPointerException());
        }
        return new LuceneIndexer(uid, account);
    }
    // 读出MessageJSON文本
    private Map<String, String> readJSONFile(Path path){
        String sourceStr = UMailUtils.file2Str(path.toFile(), "utf-8");
        Map<String,Object> sourceMsg = (Map<String,Object>)JSONObject.parse(sourceStr);

        Map<String, String> msgInfo  = new HashMap<>();
        StringBuffer buffer = null;
        JSONArray jsonArray = null;
        // Date
        msgInfo.put(DATE, (String)sourceMsg.get(DATE));
        // From
        buffer = new StringBuffer();
        jsonArray = (JSONArray)sourceMsg.get(FROM);
        for(Object oo: jsonArray){
            buffer.append((String)oo).append("  ");
        }
        msgInfo.put(FROM, buffer.toString());
        // To
        buffer = new StringBuffer();
        jsonArray = (JSONArray)sourceMsg.get(TO);
        for(Object oo: jsonArray){
            buffer.append((String)oo).append("  ");
        }
        msgInfo.put(TO, buffer.toString());
        // Subject
        msgInfo.put(SUBJECT, (String)sourceMsg.get(SUBJECT));
        // MsgId
        msgInfo.put(MESSAGE_ID, (String)sourceMsg.get(MESSAGE_ID));
        // Content
        StringBuilder contents = new StringBuilder();
        jsonArray = (JSONArray)sourceMsg.get(CONTENT);
        for(Object oo: jsonArray){
            JSONObject obj = (JSONObject)oo;
            if(obj.get("type").equals(TEXT_HTML)) {// 跳过
                contents.append(UMailUtils.html2Str((String)obj.get("text")));
            }else if(obj.get("type").equals(TEXT_PLAIN)){
                contents.append(obj.get("text"));
            }
        }
        msgInfo.put(CONTENT, contents.toString());
        // Attachment
        StringBuilder attachments = new StringBuilder();
        jsonArray = (JSONArray)sourceMsg.get(ATTACHMENTS);
        for(Object oo: jsonArray){
            JSONObject obj = (JSONObject)oo;
            attachments.append(obj.get("name"));
        }
        msgInfo.put(ATTACHMENTS, attachments.toString());
        msgInfo.put(BOX, (String)sourceMsg.get(BOX));
        msgInfo.put(MESSAGE_UID, (String)sourceMsg.get(MESSAGE_UID));
        return msgInfo;
    }

    /**
     * 建立索引
     * @throws UMailException 索引异常
     */
    public void index() throws UMailException{
        index0(indexPath, storagePath);
    }
    // 建立索引
    private void index0(Path indexPath, Path storagePath) throws UMailException{
        logger.info("建立索引............");
        // 建立文件索引对象
        Analyzer analyzer = new JcsegAnalyzer(JcsegTaskConfig.COMPLEX_MODE);
        try {
            // 索引
            Directory directory = FSDirectory.open(indexPath);
            IndexWriterConfig iwConfig = new IndexWriterConfig(analyzer);
            // IndexWriterConfig.OpenMode.CREATE设置重写索引
            // Creates a new index or overwrites an existing one.
            iwConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            IndexWriter iwriter = new IndexWriter(directory, iwConfig);
            // 遍历文件
            Files.walkFileTree(storagePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    if(!file.getParent().toFile().getName().equals("草稿箱")){
                        // 读取MessageXML文件
                        Map<String, String> msgInfo = readJSONFile(file);
                        logger.info("添加document:"+msgInfo.toString());
                        // 写入索引
                        Document doc = new Document();
                        doc.add(new StringField(BOX, notNull(msgInfo.get(BOX)), Field.Store.YES));
                        doc.add(new StringField(MESSAGE_UID, notNull(msgInfo.get(MESSAGE_UID)), Field.Store.YES));
                        doc.add(new StringField(MESSAGE_ID, notNull(msgInfo.get(MESSAGE_ID)), Field.Store.YES));
                        doc.add(new StringField(DATE, notNull(msgInfo.get(DATE)), Field.Store.YES));
                        doc.add(new TextField(FROM, notNull(msgInfo.get(FROM)), Field.Store.YES));
                        doc.add(new TextField(TO, notNull(msgInfo.get(TO)), Field.Store.YES));
                        doc.add(new TextField(SUBJECT, notNull(msgInfo.get(SUBJECT)), Field.Store.YES));
                        doc.add(new TextField(CONTENT, notNull(msgInfo.get(CONTENT)), Field.Store.NO));
                        doc.add(new TextField(ATTACHMENTS, notNull(msgInfo.get(ATTACHMENTS)), Field.Store.NO));

                        iwriter.addDocument(doc);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            iwriter.commit();
            iwriter.close();
            logger.info("索引完成............");
        }catch (IOException e){
            throw new UMailException("邮件索引建立异常", e);
        }
    }
    // 保证索引的字段不为null
    private String notNull(String source){
        return (source== null) ? "" : source;
    }
}
