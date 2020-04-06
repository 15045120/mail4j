## 使用lucene全文搜索
### 使用luke出现问题read past EOF
https://github.com/DmitryKey/luke/releases下载跟lucene版本相同的luke
这里lucene使用的是7.3.1,所以luke也是7.3.1
### queryparsersyntax如下
http://lucene.apache.org/core/3_5_0/queryparsersyntax.html
### 默认搜索的域f：
/** Create a query parser.
*  @param f  the default field for query terms.
*  @param a   used to find terms in the query text.
*/
public QueryParser(String f, Analyzer a) {
}