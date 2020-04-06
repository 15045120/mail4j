## quartz-scheduler：
### 使用方法
http://www.quartz-scheduler.org/documentation/quartz-2.2.x/quick-start.html#quartz-quick-start-guide

### 调用出现问题
```
org.quartz.SchedulerException: Problem instantiating class 'ServiceTest$SendJob'
```  
因为Job调度和Job的实现类在同一个java文件中，Job的实现类不是public，这里指java源文件中的public，
不包含内部类中public类，所以无法被实例化

### smtp发送出现535 Error
很多人认为用telnet 25 或socket 通讯的方式与smtp服务器交互,可以确认某个邮件帐号是否存在,
例如输入想要测试的邮箱帐号,再随便输个密码,希望服务器返回帐号密码错误的信息,以确认邮箱的确存在.
但实际上这种方法多数情况下是不行的.
因为即使帐号存在,密码错误,或帐号错误返回的信息都是一样的,
都是:telnet smtp 535 Error: authentication failed.从而无法判断.
