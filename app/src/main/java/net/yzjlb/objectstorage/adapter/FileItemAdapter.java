package net.yzjlb.objectstorage.adapter;



import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;

import com.qiniu.storage.BucketManager;
import com.qiniu.storage.model.FileInfo;

import net.yzjlb.objectstorage.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/*
① 创建一个继承RecyclerView.Adapter<VH>的Adapter类
② 创建一个继承RecyclerView.ViewHolder的静态内部类
③ 在Adapter中实现3个方法：
   onCreateViewHolder()
   onBindViewHolder()
   getItemCount()
*/
public class FileItemAdapter extends RecyclerView.Adapter<FileItemAdapter.MyViewHolder>{
    private Context context;
    private ArrayList<FileInfo> list_fileinfo;
    private View inflater;
    private OnItemClickListener onItemClickListener;
    //构造方法，传入数据
    public FileItemAdapter(Context context, ArrayList<FileInfo> list_fileinfo){
        this.context = context;
        this.list_fileinfo = list_fileinfo;
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        //创建ViewHolder，返回每一项的布局
        inflater = LayoutInflater.from(context).inflate(R.layout.list_item_fileinfo,parent,false);
        MyViewHolder myViewHolder = new MyViewHolder(inflater);
        return myViewHolder;
    }

    public void setOnItemClickListener(OnItemClickListener listener){
        this.onItemClickListener = listener;
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        //将数据和控件绑定
        try{

            FileInfo fileInfo = list_fileinfo.get(position);
            String name = fileInfo.key;
            String road =  fileInfo.key;
            String size = ""+fileInfo.fsize;
            holder.text_filename.setText(name);
            holder.text_size.setText(getPrintSize(fileInfo.fsize));
            Date date = new Date();
            date.setTime(fileInfo.putTime/10000);
            System.out.println(new SimpleDateFormat().format(date));


            holder.text_datetime.setText(new SimpleDateFormat().format(date).toString());
            holder.layout_root.setClickable(true);

            holder.layout_root.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.i("Share", "onClick: ");
                    if(onItemClickListener!=null){
                        onItemClickListener.onItemClickListener(name, position);
                    }
                }
            });
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    @Override
    public int getItemCount() {
        //返回Item总条数
        return list_fileinfo.size();
    }

    /**
     * 字节转kb/mb/gb
     * @param size
     * @return
     */
    public static String getPrintSize(long size) {
        //如果字节数少于1024，则直接以B为单位，否则先除于1024，后3位因太少无意义
        if (size < 1024) {
            return String.valueOf(size) + "B";
        } else {
            size = size / 1024;
        }
        //如果原字节数除于1024之后，少于1024，则可以直接以KB作为单位
        //因为还没有到达要使用另一个单位的时候
        //接下去以此类推
        if (size < 1024) {
            return String.valueOf(size) + "KB";
        } else {
            size = size / 1024;
        }
        if (size < 1024) {
            //因为如果以MB为单位的话，要保留最后1位小数，
            //因此，把此数乘以100之后再取余
            size = size * 100;
            return String.valueOf((size / 100)) + "."
                    + String.valueOf((size % 100)) + "MB";
        } else {
            //否则如果要以GB为单位的，先除于1024再作同样的处理
            size = size * 100 / 1024;
            return String.valueOf((size / 100)) + "."
                    + String.valueOf((size % 100)) + "GB";
        }
    }


    public FileInfo getItem(int index){
        return list_fileinfo.get(index);
    }

    //内部类，绑定控件
    class MyViewHolder extends RecyclerView.ViewHolder{
        LinearLayout layout_root;
        ImageView img_type;
        TextView text_filename;
        TextView text_size;
        TextView text_datetime;
        public MyViewHolder(View itemView) {
            super(itemView);
            layout_root = (LinearLayout)itemView;
            text_filename = (TextView) itemView.findViewById(R.id.text_filename);
            text_size = itemView.findViewById(R.id.text_size);
            img_type = itemView.findViewById(R.id.img_type);
            text_datetime = itemView.findViewById(R.id.text_datetime);
        }
    }

    public interface OnItemClickListener{
        public void onItemClickListener(String name, int index);
    }
}
