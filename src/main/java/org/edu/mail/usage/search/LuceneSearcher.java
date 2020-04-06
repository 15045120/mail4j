package org.edu.mail.usage.search;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.edu.mail.usage.UMailException;
import org.edu.mail.usage.util.UMailUtils;
import org.lionsoul.jcseg.analyzer.JcsegAnalyzer;
import org.lionsoul.jcseg.tokenizer.core.JcsegTaskConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.edu.mail.usage.search.IndexTerm.*;
import static org.edu.mail.usage.search.IndexTerm.SUBJECT;
import static org.edu.mail.usage.search.IndexTerm.TO;

/**
 * Created by ibu on 2019/2/28.
 */
public class LuceneSearcher extends LuceneService{
    private LuceneSearcher(String uid, String account){
        super(uid, account);
    }
    // 获取邮件接收对象
    public static LuceneSearcher newInstance(String uid, String account) throws UMailException {
        if(StringUtils.isEmpty(account)){
            throw new UMailException("账号不能为空", new NullPointerException());
        }
        return new LuceneSearcher(uid, account);
    }
    /**
     * Builder类
     * 用于构建索引查询条件
     */
    public class Builder {
        private final Map<String, String> queryItems = new HashMap<>();

        /**
         * 查询邮件发件人
         * @param from 邮件发件人
         * @return Builder对象
         */
        public LuceneSearcher.Builder withFrom(String from) {
            if (!StringUtils.isEmpty(from)) {
                logger.info("addIndexTerm:("+IndexTerm.FROM+","+from+")");
                this.queryItems.put(IndexTerm.FROM, from);
            }
            return this;
        }

        /**
         * 查询邮件收件人
         * @param to 邮件收件人
         * @return Builder对象
         */
        public LuceneSearcher.Builder withTo(String to) {
            if (!StringUtils.isEmpty(to)) {
                logger.info("addIndexTerm:("+IndexTerm.TO+","+to+")");
                this.queryItems.put(IndexTerm.TO, to);
            }
            return this;
        }

        /**
         * 查询邮件主题
         * @param subject 邮件主题
         * @return Builder对象
         */
        public LuceneSearcher.Builder withSubject(String subject) {
            if (!StringUtils.isEmpty(subject)) {
                logger.info("addIndexTerm:("+IndexTerm.SUBJECT+","+subject+")");
                this.queryItems.put(IndexTerm.SUBJECT, subject);
            }
            return this;
        }

        /**
         * 查询邮件正文
         * @param content 邮件正文
         * @return Builder对象
         */
        public LuceneSearcher.Builder withContent(String content) {
            if (!StringUtils.isEmpty(content)) {
                logger.info("addIndexTerm:("+IndexTerm.CONTENT+","+content+")");
                this.queryItems.put(IndexTerm.CONTENT, content);
            }
            return this;
        }

        /**
         * 查询指定时间段内的邮件 [2019-01-22 TO 2019-02-22]
         * @param from 开始日期
         * @param to 结束日期
         * @return Builder对象
         */
        public LuceneSearcher.Builder withDate(Date from, Date to) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            StringBuffer buffer = new StringBuffer();
            String dateStr = buffer.append("[").append(format.format(from)).append(" TO ")
                    .append(format.format(to)).append("]").toString();
            logger.info("addIndexTerm:("+IndexTerm.DATE+","+dateStr+")");
            this.queryItems.put(IndexTerm.DATE, dateStr);
            return this;
        }

        /**
         * 查询邮件附件名
         * @param attachment 邮件附件名
         * @return Builder对象
         */
        public LuceneSearcher.Builder withAttachment(String attachment) {
            if (!StringUtils.isEmpty(attachment)) {
                logger.info("addIndexTerm:("+IndexTerm.ATTACHMENTS+","+attachment+")");
                this.queryItems.put(IndexTerm.ATTACHMENTS, attachment);
            }
            return this;
        }

        // QueryParserSyntax：AND的使用
        private String parse(){
            StringBuilder buffer = new StringBuilder();
            int count = 0;
            for (Map.Entry entry:
                    queryItems.entrySet()) {
                if((++count) < queryItems.size()){
                    if((entry.getKey()).equals(DATE)){
                        buffer.append(entry.getKey()).append(":").append(entry.getValue()).append(" AND ");
                    }else {
                        buffer.append(entry.getKey()).append(":\"").append(entry.getValue()).append("\"").append(" AND ");
                    }
                    continue;
                }
                if((entry.getKey()).equals(DATE)){
                    buffer.append(entry.getKey()).append(":").append(entry.getValue());
                }else {
                    buffer.append(entry.getKey()).append(":\"").append(entry.getValue()).append("\"");
                }
            }
            return buffer.toString();
        }

        /**
         * 邮件搜索
         * @param hitsPerPage 页数
         * @return 邮件JSON对象数组
         * @throws UMailException 邮件搜索异常
         */
        public Object[] search(int hitsPerPage) throws UMailException{
            String queryStr = parse();
            return LuceneSearcher.this.search(queryStr, hitsPerPage);
        }
    }

    /**
     * 使用建造者模式构建查询条件
     * <code>
     * new LuceneSearcher(uid, account).create().withFrom("").withTo("")
     *         .search(1);
     * </code>
     * @return LuceneSearcher.Builder对象
     */
    public LuceneSearcher.Builder create() {
        return new LuceneSearcher.Builder();
    }

    /**
     * 全文搜索，包括邮件收发件人名，邮件主题，邮件正文，邮件附件
     * @param queryAll 查询关键词
     * @param hitsPerPage 页数
     * @return 邮件JSON对象数组
     * @throws UMailException 邮件搜索异常
     */
    public Object[] searchAll(String queryAll, int hitsPerPage) throws UMailException{
        StringBuffer buffer = new StringBuffer();
        buffer.append(FROM).append(":\"").append(queryAll).append("\"").append(" OR ")
                .append(TO).append(":\"").append(queryAll).append("\"").append(" OR ")
                .append(SUBJECT).append(":\"").append(queryAll).append("\"").append(" OR ")
                .append(CONTENT).append(":\"").append(queryAll).append("\"").append(" OR ")
                .append(ATTACHMENTS).append(":\"").append(queryAll).append("\"");
        return doSearch(indexPath, buffer.toString(), hitsPerPage);
    }


    private Object[] search(String queryString, int hitsPerPage) throws UMailException{
         return doSearch(indexPath, queryString, hitsPerPage);
    }
    private Object[] doSearch(Path indexPath, String queryString, int hitsPerPage) throws UMailException{
        logger.info(queryString);
        List<Object> list = new ArrayList<>();
        try {
            // 建立文件索引对象
            Analyzer analyzer = new JcsegAnalyzer(JcsegTaskConfig.COMPLEX_MODE);
            Directory directory = FSDirectory.open(indexPath);
            IndexReader indexReader = DirectoryReader.open(directory);
            IndexSearcher indexSearcher = new IndexSearcher(indexReader);
            QueryParser queryParser = new QueryParser(CONTENT, analyzer);
            Query query = queryParser.parse(queryString);
            TopDocs topDocs = indexSearcher.search(query, 10 * hitsPerPage);
            ScoreDoc[] scoreDocs = topDocs.scoreDocs;

            for (ScoreDoc scDoc : scoreDocs) {
                Document targetDoc = indexSearcher.doc(scDoc.doc);
                String file = storagePath+separator+targetDoc.get(BOX)+separator+targetDoc.get(MESSAGE_UID)+".json";
                Object json = JSONObject.parse(UMailUtils.file2Str(new File(file), "utf-8"));
                list.add(json);
                logger.info("共搜索到"+list.size()+"封邮件");
            }
            indexReader.close();
            return list.toArray();
        }catch (IOException e1){
            throw new UMailException("邮件索引搜索异常", e1);
        }catch (ParseException e2){
            throw new UMailException("邮件索引搜索异常", e2);
        }
    }
}
