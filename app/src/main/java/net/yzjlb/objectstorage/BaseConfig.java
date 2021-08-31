package net.yzjlb.objectstorage;

public class BaseConfig {
    public static String accessKey = "";
    public static String secretKey = "";
    public static String bucket = "";
    public static String token = "";
    public static int objectTimeOut = 3600; //一小时
    public static String objectUrl = "";//空间绑定的域名

    //赞助地址
    public static String sponsorUrl = "http://app.yzjlb.net/app/api.php?type=sponsor&package="+BuildConfig.APPLICATION_ID; //赞助地址

}
