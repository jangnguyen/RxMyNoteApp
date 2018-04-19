package vn.mb360.rxnote.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import vn.mb360.rxnote.R;
import vn.mb360.rxnote.network.model.Note;

import static android.content.ContentValues.TAG;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.MyViewHolder> {

    private Context context;
    private List<Note> noteList;

    public NotesAdapter(Context context, List<Note> noteList) {
        this.context = context;
        this.noteList = noteList;
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.note_list_row, parent, false);

        return new MyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        Note note = noteList.get(position);

        // Display dot from HTML char code
        holder.note.setText(note.getNote());
        // Changing dot color to random color
        holder.dot.setText(Html.fromHtml("&#8226;"));
        holder.dot.setTextColor(getRandomMaterialColor("400"));
        // Formatting and displaying timestamp
        holder.timestamp.setText(formatDate(note.getTimestamp()));
    }

    private int getRandomMaterialColor(String typeColor) {
        int returnColor = Color.GRAY;
        int arrayId = context.getResources().getIdentifier("mdcolor_" + typeColor, "array", context.getPackageName());

        if (arrayId != 0) {
            TypedArray colors = context.getResources().obtainTypedArray(arrayId);
            int index = (int) (Math.random() * colors.length());
            returnColor = colors.getColor(index, Color.GRAY);
            colors.recycle();
        }

        return returnColor;
    }

    private String formatDate(String timestamp) {
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date date = fmt.parse(timestamp);
            SimpleDateFormat fmtOut = new SimpleDateFormat("MMMM d");
            return fmtOut.format(date);
        } catch (ParseException e) {
            Log.e(TAG, "formatDate: ", e);
        }

        return "";
    }

    @Override
    public int getItemCount() {
        return noteList.size();
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.note)
        TextView note;

        @BindView(R.id.dot)
        TextView dot;

        @BindView(R.id.timestamp)
        TextView timestamp;

        public MyViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }
}
