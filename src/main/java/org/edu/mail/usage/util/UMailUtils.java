package org.edu.mail.usage.util;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.Zip4jConstants;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.io.*;
import java.nio.Buffer;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

public class UMailUtils {
    private UMailUtils(){

    }
    /**
     * 压缩文件
     * @param input 待压缩文件
     * @param password 密码
     * @return  压缩后文件
     * @throws ZipException 压缩异常
     */
    public static File zipAndEncrypt(File input, String password) throws ZipException {
        String fileName = input.getName();
        String  zipfileName = input.getParent() + "//ENCRYPT_MAIL_EML.zip";
        ZipFile zipFile = new ZipFile(zipfileName);
        ZipParameters parameters = new ZipParameters();
        parameters.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);
        parameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);
        parameters.setEncryptFiles( true );
        parameters.setEncryptionMethod(Zip4jConstants.ENC_METHOD_AES);
        parameters.setAesKeyStrength(Zip4jConstants.AES_STRENGTH_256);
        parameters.setPassword(password);
        zipFile.addFile(input, parameters);
        return new File(zipfileName);
    }

    /**
     * 从文件中读取字符串
     * @param file 文件
     * @return 文件文本内容
     */
    public static String file2Str(File file){
        String content = "";
        InputStream is = null;
        try{
            is = new FileInputStream(file);
            byte[] bytes = new byte[1024];
            ByteArrayOutputStream byteArray =  new ByteArrayOutputStream();
            int len = 0;
            while(-1 != (len = is.read(bytes))){
                byteArray.write(bytes, 0, len);
            }
            if(is != null){
                is.close();
            }
            content =  byteArray.toString();
        }catch(FileNotFoundException e0){
            e0.printStackTrace();
        } finally {
            return content;
        }
    }

    /**
     * 从文件中读取字符串
     * @param file 文件
     * @param encode 文件编码格式
     * @return 文件文本内容
     */
    public static String file2Str(File file, String encode){
        StringBuffer buffer = new StringBuffer();
        try{
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), encode));
            String line = "";
            while((line=reader.readLine()) != null){
                buffer.append(line);
            }
            reader.close();
        }catch(FileNotFoundException e0){
        } finally {
            return buffer.toString();
        }
    }

    /**
     * 复制文件
     * @param from 复制前路径
     * @param to 复制后路径
     * @return true复制成功，false复制失败
     */
    public static boolean copyFile(String from, String to){
        boolean success = true;
        try {
            FileInputStream fis = new FileInputStream(new File(from));
            FileOutputStream fos = new FileOutputStream(new File(to));
            int one = 0;
            while ((one = fis.read()) != -1) {
                fos.write(one);
            }
            fis.close();
            fos.close();
        }catch (IOException e){
            success = false;
        }finally {
            return success;
        }
    }
    // 提取文字
    private static String extractText(Node node){
        if(node instanceof TextNode){
            return ((TextNode) node).text();
        }
        List<Node> children = node.childNodes();
        StringBuffer buffer = new StringBuffer();
        for (Node child: children) {
            buffer.append(extractText(child));
        }
        return buffer.toString();
    }

    /**
     * 使用jsoup解析html并转化为提取字符串
     * @param html html文件
     * @return 纯文本文件
     */
    public static String html2Str(String html){
        Document doc = Jsoup.parse(html);
        return extractText(doc);
    }

    /**
     * 编码成base64格式, URL and Filename safe type base64 encoding scheme.
     * @param is 输入字节流
     * @return 编码后字符串
     */
    public static String encode64(InputStream is){
        /*解决方法：将字节全部读入，最后将字节编码成base64类型字符串*/
        String base64String = "";
        try{
            byte[] bytes = new byte[1024];
            ByteArrayOutputStream byteArray =  new ByteArrayOutputStream();
            int len = 0;
            while(-1 != (len = is.read(bytes))){
                byteArray.write(bytes, 0, len);
            }
            if(is != null){
                is.close();
            }
            byte[] toBase64Bytes = byteArray.toByteArray();
            base64String =  Base64.getUrlEncoder().encodeToString(toBase64Bytes);
        }catch(FileNotFoundException e0){
            e0.printStackTrace();
        }catch(IOException e1){
            e1.printStackTrace();
        }finally {
            return base64String;
        }
    }

    /**
     * 编码成base64格式, URL and Filename safe type base64 encoding scheme.
     * @param src 输入字节
     * @return 编码后字符串
     */
    public static String encode64(byte[] src){
        if(src == null || src.length == 0){
            return "";
        }
        return Base64.getUrlEncoder().encodeToString(src);
    }

    /**
     * base64解码
     * @param src base64格式的字符串
     * @return 解码后字符串
     */
    public static String decode64(String src){
        byte[] res = Base64.getUrlDecoder().decode(src);
        return new String(res);
    }
    /**
     * JavaScript支持，替换双引号
     * @param source 源文件
     * @return 替换双引号后的字符串
     */
    public static String replaceDoubleQuotes(String source){
        String res = source.replaceAll("\"", "'");
        return res;
    }

    /**
     * 计算文件大小
     * @param file 文件
     * @return 文件大小
     */
    public static String sizeFile(File file){
        if(file == null){
            return "0KB";
        }
        double src = file.length()*1.0;
        if(Double.compare(src/1024.0, 1024.0) < 0){
            return String.format("%.2fKB", src/1024.0);
        }
        src = src/1024.0;
        if(Double.compare(src/1024, 1024.0) < 0){
            System.out.println();
            return String.format("%.2fMB", src/1024.0);
        }
        return "0KB";
    }

    /**
     * 是否为window环境
     * @return true为window环境，false为linux环境
     */
    public static boolean isWindowEnv(){
        String env = System.getProperty("os.name");
        if(env.toLowerCase().startsWith("win")){
            return true;
        }
        return false;
    }
}
