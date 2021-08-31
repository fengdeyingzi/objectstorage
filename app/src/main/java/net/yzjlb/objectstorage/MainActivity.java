package net.yzjlb.objectstorage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.storage.model.FileInfo;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;

import net.yzjlb.objectstorage.adapter.FileItemAdapter;
import net.yzjlb.objectstorage.util.SharedPreferencesUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;

public class MainActivity extends BaseActivity {
    private static final String TAG = "MainActivity";
    RecyclerView recyclerView;
    TextView text_bucket;
    Button btn_upload;
    FileItemAdapter adapter;
    Handler handler;
    //文件缓存目录
    File cacheDir;
    SwipeRefreshLayout mSwipeRefreshLayout;
    private static int REQUEST_FILE = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        handler = new Handler(getMainLooper());
        recyclerView = findViewById(R.id.listView);
        text_bucket = findViewById(R.id.text_bucket);
        btn_upload = findViewById(R.id.btn_upload);
        mSwipeRefreshLayout = findViewById(R.id.swipeRefresh);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        cacheDir = getCacheDir();
        SharedPreferencesUtil util = new SharedPreferencesUtil(this);
        BaseConfig.objectUrl = util.getString("obj_url", "");
        BaseConfig.bucket = util.getString("obj_bucket", "");
        BaseConfig.accessKey = util.getString("obj_accesskey", "");
        BaseConfig.secretKey = util.getString("obj_secretkey", "");
        btn_upload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickFile();
            }
        });


        //初始化下拉刷新
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                updateFileList();
            }
        });

    }

    private void stopRefresh(){
        //结束刷新
        mSwipeRefreshLayout.setRefreshing(false);
    }

    // 打开系统的文件选择器
    public void pickFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
//        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);//设置可以多选文件
        Intent intent2 = Intent.createChooser(intent, "title");
        this.startActivityForResult(intent2, REQUEST_FILE);
    }

    public static String getFileRealNameFromUri(Context context, Uri fileUri) {
        if (context == null || fileUri == null) return null;
        DocumentFile documentFile = DocumentFile.fromSingleUri(context, fileUri);
        if (documentFile == null) return null;
        return documentFile.getName();
    }


    //检测配置是否完整
    public boolean checkConfig() {
        if (BaseConfig.accessKey.length() > 0 && BaseConfig.bucket.length() > 0 && BaseConfig.secretKey.length() > 0) {
            return true;
        }
        return false;
    }

    public void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        text_bucket.setText(BaseConfig.bucket);
        updateFileList();
    }

    public void showItemDialog(FileInfo fileInfo) {
        new AlertDialog.Builder(this)
                .setTitle(fileInfo.key)
//                .setMessage("链接：" + BaseConfig.objectUrl + "/" + fileInfo.key)
                .setItems(new String[]{"复制链接","重命名","删除"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which){
                            case 0:
                                ClipboardManager clipboarManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                try {
                                    clipboarManager.setText(BaseConfig.objectUrl + "/" + URLEncoder.encode(fileInfo.key, "UTF-8"));
                                    toast("文本已复制到系统剪切板");
                                } catch (UnsupportedEncodingException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 1:
                                EditText edit_name = new EditText(MainActivity.this);
                                edit_name.setText(fileInfo.key);
                                edit_name.setHint("请输入新文件名");
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("重命名")
                                        .setView(edit_name)
                                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                new Thread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        renameFile(fileInfo.key, edit_name.getText().toString(), new OnUpload() {
                                                            @Override
                                                            public void onSuccess(String text) {

                                                                toast("重命名成功："+text);
                                                                updateFileList();
                                                            }

                                                            @Override
                                                            public void onError(Exception e) {
                                                                showInfoDialog(e.toString());
                                                            }
                                                        });
                                                    }
                                                }).start();
                                            }
                                        }).setNeutralButton("取消",null).create().show();
                                break;
                            case 2:
                                showProgressDialog("删除中。。。");
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        deleteFile(BaseConfig.bucket, fileInfo.key, new OnUpload() {
                                            @Override
                                            public void onSuccess(String text) {
                                                toast("删除成功:" + text);
                                                dismissDialog();
                                                updateFileList();
                                            }

                                            @Override
                                            public void onError(Exception e) {
                                                toast(e.toString());
                                                showInfoDialog(e.toString());
                                                dismissDialog();
                                            }
                                        });
                                    }
                                }).start();

                        }
                    }
                })
//                .setPositiveButton("复制链接", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        ClipboardManager clipboarManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
//                        try {
//                            clipboarManager.setText(BaseConfig.objectUrl + "/" + URLEncoder.encode(fileInfo.key, "UTF-8"));
//                            toast("文本已复制到系统剪切板");
//                        } catch (UnsupportedEncodingException e) {
//                            e.printStackTrace();
//                        }
//
//                    }
//                })
//                .setNeutralButton("删除", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        showProgressDialog("删除中。。。");
//                        new Thread(new Runnable() {
//                            @Override
//                            public void run() {
//                                deleteFile(BaseConfig.bucket, fileInfo.key, new OnUpload() {
//                                    @Override
//                                    public void onSuccess(String text) {
//                                        toast("删除成功:" + text);
//                                        dismissDialog();
//                                        updateFileList();
//                                    }
//
//                                    @Override
//                                    public void onError(Exception e) {
//                                        toast(e.toString());
//                                        showInfoDialog(e.toString());
//                                        dismissDialog();
//                                    }
//                                });
//                            }
//                        }).start();
//
//                    }
//                })
//                .setNeutralButton("重命名", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        EditText edit_name = new EditText(MainActivity.this);
//                        edit_name.setText(fileInfo.key);
//                        edit_name.setHint("请输入新文件名");
//                        new AlertDialog.Builder(MainActivity.this)
//                                .setTitle("重命名")
//                                .setView(edit_name)
//                                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
//                                    @Override
//                                    public void onClick(DialogInterface dialog, int which) {
//                                        new Thread(new Runnable() {
//                                            @Override
//                                            public void run() {
//                                                renameFile(fileInfo.key, edit_name.getText().toString(), new OnUpload() {
//                                                    @Override
//                                                    public void onSuccess(String text) {
//
//                                                        toast("重命名成功："+text);
//                                                        updateFileList();
//                                                    }
//
//                                                    @Override
//                                                    public void onError(Exception e) {
//showInfoDialog(e.toString());
//                                                    }
//                                                });
//                                            }
//                                        }).start();
//                                    }
//                                }).setNeutralButton("取消",null).create().show();
//
//                    }
//                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                }).create().show();

    }

    public void updateFileList() {
        if(checkConfig()){
            toast("更新文件列表");
            showProgressDialog("加载文件列表");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (checkConfig()) {
                        Log.i(TAG, "run: 检测成功");
                        getFileList(BaseConfig.bucket, new OnObjectFileList() {
                            @Override
                            public void onGetFileList(ArrayList<FileInfo> list_fileinfo) {
                                dismissDialog();
                                stopRefresh();
                                adapter = new FileItemAdapter(MainActivity.this, list_fileinfo);
                                adapter.setOnItemClickListener(new FileItemAdapter.OnItemClickListener() {
                                    @Override
                                    public void onItemClickListener(String name, int index) {

                                        showItemDialog(adapter.getItem(index));
                                    }
                                });
                                recyclerView.setAdapter(adapter);
                                toast("更新成功" + adapter.getItemCount());
                            }

                            @Override
                            public void onGetFileErr(Exception e) {
                                stopRefresh();
                                toast(e.toString());
                                showInfoDialog(e.toString());
                                dismissDialog();
                            }
                        });
                    }

                }
            }).start();
        } else{
            toast("请先配置七牛云");
        }

    }

    //获取简单上传凭证
    public String getUpKey(String bucket) {
        String accessKey = BaseConfig.accessKey;
        String secretKey = BaseConfig.secretKey;
//        String bucket = "bucket name";
        Auth auth = Auth.create(accessKey, secretKey);
        String upToken = auth.uploadToken(bucket);
        System.out.println(upToken);
        return upToken;
    }

    //获取覆盖上传凭证
    public String getUpKeyX(String bucket, String key) {
        String accessKey = BaseConfig.accessKey;
        String secretKey = BaseConfig.secretKey;
//        String bucket = bucket;
//        String key = "file key";
        Auth auth = Auth.create(accessKey, secretKey);
        String upToken = auth.uploadToken(bucket, key);
        System.out.println(upToken);
        return upToken;
    }

    //文件重命名
    public void renameFile(String oldname, String newname, OnUpload onUpload){
//构造一个带指定 Region 对象的配置类
        Configuration cfg = new Configuration(Region.region0());
//...其他参数参考类注释

        String accessKey = BaseConfig.accessKey;
        String secretKey = BaseConfig.secretKey;

        String fromBucket = BaseConfig.bucket;
        String fromKey = oldname;
        String toBucket = BaseConfig.bucket;
        String toKey = newname;

        Auth auth = Auth.create(accessKey, secretKey);
        BucketManager bucketManager = new BucketManager(auth, cfg);
        try {
            bucketManager.move(fromBucket, fromKey, toBucket, toKey);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    onUpload.onSuccess(newname);
                }
            });
        } catch (QiniuException ex) {
            //如果遇到异常，说明移动失败
            System.err.println(ex.code());
            System.err.println(ex.response.toString());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    onUpload.onError(ex);
                }
            });
        }


    }

    //获取文件列表
    public void getFileList(String bucket, OnObjectFileList onObjectFileList) {
        //构造一个带指定 Region 对象的配置类
        Configuration cfg = new Configuration(Region.autoRegion());
//...其他参数参考类注释

        String accessKey = BaseConfig.accessKey;
        String secretKey = BaseConfig.secretKey;

//        String bucket = "your bucket name";

        Auth auth = Auth.create(accessKey, secretKey);
        BucketManager bucketManager = new BucketManager(auth, cfg);

//文件名前缀
        String prefix = "";
//每次迭代的长度限制，最大1000，推荐值 1000
        int limit = 1000;
//指定目录分隔符，列出所有公共前缀（模拟列出目录效果）。缺省值为空字符串
        String delimiter = "";

//列举空间文件列表
        try {
            BucketManager.FileListIterator fileListIterator = bucketManager.createFileListIterator(bucket, prefix, limit, delimiter);
            ArrayList<FileInfo> list_fileinfo = new ArrayList<>();
            while (fileListIterator.hasNext()) {
                //处理获取的file list结果
                FileInfo[] items = fileListIterator.next();
                for (FileInfo item : items) {
                    list_fileinfo.add(item);
                    System.out.println(item.key);
                    System.out.println(item.hash);
                    System.out.println(item.fsize);
                    System.out.println(item.mimeType);
                    System.out.println(item.putTime);
                    System.out.println(item.endUser);
                }
            }
            handler.post(new Runnable() {
                @Override
                public void run() {
                    onObjectFileList.onGetFileList(list_fileinfo);
                }
            });
        } catch (Exception e) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    onObjectFileList.onGetFileErr(e);
                }
            });
        }


    }

    //上传本地文件
    public void uploadFile(String bucket, File file, OnUpload onUpload) {
        //构造一个带指定 Region 对象的配置类
        Configuration cfg = new Configuration(Region.autoRegion());
//...其他参数参考类注释

        UploadManager uploadManager = new UploadManager(cfg);
//...生成上传凭证，然后准备上传
        String accessKey = BaseConfig.accessKey;
        String secretKey = BaseConfig.secretKey;
//        String bucket = ;
//如果是Windows情况下，格式是 D:\\qiniu\\test.png
        String localFilePath = file.getAbsolutePath();
//默认不指定key的情况下，以文件内容的hash值作为文件名
        String key = file.getName();

        Auth auth = Auth.create(accessKey, secretKey);
        String upToken = auth.uploadToken(bucket);

        try {
            Response response = uploadManager.put(localFilePath, key, upToken);
            Log.i(TAG, "uploadFile: " + response.bodyString());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    onUpload.onSuccess(file.getName());
                }
            });

            //解析上传成功的结果
//            DefaultPutRet putRet = new Gson().fromJson(response.bodyString(), DefaultPutRet.class);
//            System.out.println(putRet.key);
//            System.out.println(putRet.hash);
        } catch (QiniuException ex) {
            Response r = ex.response;
            System.err.println(r.toString());
            try {
                System.err.println(r.bodyString());
            } catch (QiniuException ex2) {
                //ignore
            }
            handler.post(new Runnable() {
                @Override
                public void run() {
                    onUpload.onError(ex);
                }
            });

        }

    }

    //自定义参数上传
    public void uploadFileAndSetTag(String bucket, File file, StringMap params) {
        //构造一个带指定 Region 对象的配置类
        Configuration cfg = new Configuration(Region.autoRegion());
        UploadManager uploadManager = new UploadManager(cfg);
//...其他参数参考类注释

//...生成上传凭证，然后准备上传
        String accessKey = BaseConfig.accessKey;
        String secretKey = BaseConfig.secretKey;
//        String bucket = "your bucket name";
//如果是Windows情况下，格式是 D:\\qiniu\\test.png
        String localFilePath = file.getAbsolutePath();
//默认不指定key的情况下，以文件内容的hash值作为文件名
//设置上传后的文件名称
        String key = file.getName();

        Auth auth = Auth.create(accessKey, secretKey);

//上传自定义参数，自定义参数名称需要以 x:开头
//        StringMap params = new StringMap();
//        params.put("x:fname","123.jpg");
//        params.put("x:age",20);

//上传策略
        StringMap policy = new StringMap();
//自定义上传后返回内容，返回自定义参数，需要设置 x:参数名称，注意下面
        policy.put("returnBody", "{\"key\":\"$(key)\",\"hash\":\"$(etag)\",\"fname\":\"$(x:fname)\",\"age\",$(x:age)}");
//生成上传token
        String upToken = auth.uploadToken(bucket, key, BaseConfig.objectTimeOut, policy);
        try {
            Response response = uploadManager.put(localFilePath, key, upToken, params, null, false);
            System.out.println(response.bodyString());
        } catch (QiniuException ex) {
            Response r = ex.response;
            System.err.println(r.toString());
            try {
                System.err.println(r.bodyString());
            } catch (QiniuException ex2) {
                //ignore
            }
        }

    }

    //获取文件信息
    public void getFileInfo(String bucket, String key) {
        //构造一个带指定 Region 对象的配置类
        Configuration cfg = new Configuration(Region.autoRegion());
//...其他参数参考类注释

        String accessKey = BaseConfig.accessKey;
        String secretKey = BaseConfig.secretKey;
//        String bucket = "your bucket name";
//        String key = "your file key";

        Auth auth = Auth.create(accessKey, secretKey);
        BucketManager bucketManager = new BucketManager(auth, cfg);
        try {
            FileInfo fileInfo = bucketManager.stat(bucket, key);
            System.out.println(fileInfo.hash);
            System.out.println(fileInfo.fsize);
            System.out.println(fileInfo.mimeType);
            System.out.println(fileInfo.putTime);
        } catch (QiniuException ex) {
            System.err.println(ex.response.toString());
        }

    }

    //删除文件
    public boolean deleteFile(String bucket, String key, OnUpload onUpload) {
        //构造一个带指定 Region 对象的配置类
        Configuration cfg = new Configuration(Region.autoRegion());
//...其他参数参考类注释

        String accessKey = BaseConfig.accessKey;
        String secretKey = BaseConfig.secretKey;

//    String bucket = "your bucket name";
//    String key = "your file key";

        Auth auth = Auth.create(accessKey, secretKey);
        BucketManager bucketManager = new BucketManager(auth, cfg);
        try {
            bucketManager.delete(bucket, key);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    onUpload.onSuccess(key);
                }
            });

            return true;
        } catch (QiniuException ex) {
            //如果遇到异常，说明删除失败
            System.err.println(ex.code());
            System.err.println(ex.response.toString());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    onUpload.onError(ex);
                }
            });
            return false;
        }

    }

    public interface OnObjectFileList {
        public void onGetFileList(ArrayList<FileInfo> list_fileinfo);

        public void onGetFileErr(Exception e);
    }

    public interface OnUpload {
        public void onSuccess(String text);

        public void onError(Exception e);
    }
    //获取域名列表


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 1, "设置");
        menu.add(0, 2, 1, "关于");
        return super.onCreateOptionsMenu(menu);

    }

    void N2J_wap(String http) {
		/*
		 Uri uri=Uri.parse(http);
		 Intent intent=new Intent( Intent.ACTION_VIEW ,uri );
		 run_activity.startActivity(intent);
		 */
        Intent intent = new Intent();

        intent.setAction("android.intent.action.VIEW");
        Uri content_url = Uri.parse(http);
        intent.setData(content_url);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "请下载网页浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                Intent intent = new Intent(this, SettingActivity.class);
                startActivity(intent);
                break;
            case 2:
                new AlertDialog.Builder(this)
                        .setTitle("关于")
                        .setMessage("云存储管理APP\n作者：风的影子\n更新地址：http://www.yzjlb.net/app/objectstorage\n")
                        .setPositiveButton("赞助", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                N2J_wap(BaseConfig.sponsorUrl);
                            }
                        })
                        .setNegativeButton("返回", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        }).create().show();
        }
        return super.onOptionsItemSelected(item);

    }

    public void addUri(Uri uri) {
        Log.i(TAG, "addUri: " + uri.toString());

        if (uri.toString().startsWith("content://")) {
            Cursor c = getContentResolver().query(uri, null, null, null, null);

            if (c != null && c.moveToFirst()) {
                int id = c.getColumnIndex(MediaStore.Images.Media.DATA);

                if (id != -1) {
                    String name = c.getString(id);
                    Log.i(TAG, "addUri: " + name);
                }

            }
        }
        Log.i(TAG, "addUri: " + getFileRealNameFromUri(this, uri));
        String content_name = getFileRealNameFromUri(this, uri);
        String filename = uri.getPath();
//        if(content_name==null){
//            content_name = FileUtils.getName(filename);
//        }
        toast("开始上传" + content_name);
        File extern_dir = cacheDir;
        String out_path = extern_dir.getPath() + File.separator + content_name;
        File out_file = new File(out_path);
        InputStream input = null;
        try {
//            if(uri.toString().startsWith("file://")){
//                input = new FileInputStream(uri.getPath());
//            }
//            else
            if (Build.VERSION.SDK_INT <= 23) {
                input = new FileInputStream(uri.getPath());
            } else {
                ContentResolver resolver = getContentResolver();
                input = resolver.openInputStream(uri);
            }
            OutputStream out = new FileOutputStream(out_file);
            byte[] buf = new byte[10240];
            int len = 0;
            try {
                while ((len = input.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
                out.close();
                input.close();
                //打开文件
                showProgressDialog("上传中。。。");
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        uploadFile(BaseConfig.bucket, out_file, new OnUpload() {
                            @Override
                            public void onSuccess(String text) {
                                toast("上传成功");
                                dismissDialog();
                                updateFileList();
                            }

                            @Override
                            public void onError(Exception e) {
                                toast(e.toString());
                                showInfoDialog(e.toString());
                                dismissDialog();
                                Log.e(TAG, e.getMessage());
                            }
                        });
                    }
                }).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        setIntent(new Intent());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (data == null) {
            return;
        }
        if (requestCode == REQUEST_FILE) {
            ClipData clipData = data.getClipData();
            Uri uri = null;
            if (clipData != null) {
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    ClipData.Item itemAt = clipData.getItemAt(i);
                    uri = itemAt.getUri();
                    addUri(uri);
                }
            } else {
                uri = data.getData(); // 获取用户选择文件的URI
                addUri(uri);
            }

        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}