package vn.mb360.rxnote.view;

import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Function;
import io.reactivex.observers.DisposableCompletableObserver;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;
import retrofit2.HttpException;
import vn.mb360.rxnote.R;
import vn.mb360.rxnote.network.ApiClient;
import vn.mb360.rxnote.network.ApiService;
import vn.mb360.rxnote.network.model.Note;
import vn.mb360.rxnote.network.model.User;
import vn.mb360.rxnote.utils.MyDividerItemDecoration;
import vn.mb360.rxnote.utils.PrefUtils;
import vn.mb360.rxnote.utils.RecyclerTouchListener;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    @BindView(R.id.coordinator_layout)
    CoordinatorLayout coordinatorLayout;
    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;
    @BindView(R.id.txt_empty_notes_view)
    TextView tvNoteView;

    private ApiService apiService;
    private CompositeDisposable disposable = new CompositeDisposable();
    private NotesAdapter notesAdapter;
    private List<Note> noteList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.activity_title_home);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showNoteDialog(false, null, -1);
            }
        });

        whiteNotificationBar(fab);

        apiService = ApiClient.getClient(getApplicationContext()).create(ApiService.class);

        notesAdapter = new NotesAdapter(this, noteList);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.addItemDecoration(new MyDividerItemDecoration(this, LinearLayoutManager.VERTICAL, 16));
        recyclerView.setAdapter(notesAdapter);

        /*
         * On long press on RecyclerView item, open alert dialog
         * with option to choose Edit and Delete
         * */
        recyclerView.addOnItemTouchListener(new RecyclerTouchListener(this, recyclerView, new RecyclerTouchListener.ClickListener() {
            @Override
            public void onClick(View view, int position) {

            }

            @Override
            public void onLongClick(View view, int position) {
                showActionDialog(position);
            }
        }));

        /*
         * Check for stored Api Key in sharedPreferences
         * If not, make api call to register the user
         * This will be executed when app is installed for the first time
         * or data is cleared from settings
         * */
        if (TextUtils.isEmpty(PrefUtils.getApiKey(this))) {
            registerUser();
        } else {
            fetchAllNotes();
        }
    }

    private void fetchAllNotes() {
        disposable.add(
                apiService.fetchAllNotes()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .map(new Function<List<Note>, List<Note>>() {
                            @Override
                            public List<Note> apply(List<Note> notes) throws Exception {
                                Collections.sort(notes, new Comparator<Note>() {
                                    @Override
                                    public int compare(Note o1, Note o2) {
                                        return o2.getId() - o1.getId();
                                    }
                                });

                                return notes;
                            }
                        })
                        .subscribeWith(new DisposableSingleObserver<List<Note>>() {
                            @Override
                            public void onSuccess(List<Note> notes) {
                                noteList.clear();
                                noteList.addAll(notes);
                                notesAdapter.notifyDataSetChanged();

                                toggleEmptyNotes();
                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.e(TAG, "onError: " + e.getMessage());
                                showError(e);
                            }
                        })
        );
    }

    private void registerUser() {
        String uniqueId = UUID.randomUUID().toString();
        disposable.add(
                apiService.register(uniqueId)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeWith(new DisposableSingleObserver<User>() {
                            @Override
                            public void onSuccess(User user) {
                                PrefUtils.storeApiKey(getApplicationContext(), user.getApiKey());
                                Toast.makeText(MainActivity.this, "Your device registered successfully! ApiKey: " + PrefUtils.getApiKey(getApplicationContext()), Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.e(TAG, "onError: " + e.getMessage());
                                showError(e);
                            }
                        })
        );
    }

    private void showActionDialog(final int position) {
        CharSequence colors[] = new CharSequence[]{"Edit", "Delete"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose option");
        builder.setItems(colors, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    showNoteDialog(true, noteList.get(position), position);
                } else {
                    deleteNote(noteList.get(position).getId(), position);
                }
            }
        }).show();
    }

    /*
     * DELETE A NOTE
     */
    private void deleteNote(final int noteId, final int position) {
        Log.e(TAG, "deleteNote: " + noteId + ", " + position);
        disposable.add(
                apiService.deleteNote(noteId)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeWith(new DisposableCompletableObserver() {
                            @Override
                            public void onComplete() {
                                Log.d(TAG, "Note deleted! " + noteId);

                                noteList.remove(position);
                                notesAdapter.notifyItemRemoved(position);

                                Toast.makeText(MainActivity.this, "Note deleted.", Toast.LENGTH_SHORT).show();
                                toggleEmptyNotes();
                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.e(TAG, "onError: " + e.getMessage());
                                showError(e);
                            }
                        })
        );
    }

    /**
     * Shows alert dialog with EditText options to enter / edit a note.
     * when shouldUpdate=true, it automatically displays old note and changes the
     * button text to UPDATE
     */
    private void showNoteDialog(final boolean shouldUpdate, final Note note, final int position) {

        // Render View from Layout XML
        LayoutInflater layoutInflater = LayoutInflater.from(getApplicationContext());
        View view = layoutInflater.inflate(R.layout.note_dialog, null);
        AlertDialog.Builder alertDialogBuilderUserInput = new AlertDialog.Builder(MainActivity.this);
        alertDialogBuilderUserInput.setView(view);

        final EditText inputNote = view.findViewById(R.id.note);
        TextView dialogTitle = view.findViewById(R.id.dialog_title);
        dialogTitle.setText(!shouldUpdate ? getString(R.string.lbl_new_note_title) : getString(R.string.lbl_edit_note_title));

        if (shouldUpdate && note != null) {
            inputNote.setText(note.getNote());
        }
        alertDialogBuilderUserInput
                .setCancelable(false)
                .setPositiveButton(shouldUpdate ? "update" : "save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alertDialog = alertDialogBuilderUserInput.create();
        alertDialog.show();

        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TextUtils.isEmpty(inputNote.getText().toString())) {
                    Toast.makeText(MainActivity.this, "Please enter note!", Toast.LENGTH_SHORT).show();
                    return;
                } else {
                    alertDialog.dismiss();
                }

                // Check if user updating note
                if (shouldUpdate && note != null) {
                    // Update note by ID
                    updateNote(note.getId(), inputNote.getText().toString(), position);
                } else {
                    // Create new note
                    createNote(inputNote.getText().toString());
                }
            }
        });
    }

    /*
     * CREATE A NEW NOTE
     */
    private void createNote(String note) {
        disposable.add(
                apiService.createNote(note)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeWith(new DisposableSingleObserver<Note>() {
                            @Override
                            public void onSuccess(Note note) {
                                if (!TextUtils.isEmpty(note.getError())) {
                                    Toast.makeText(MainActivity.this, note.getError(), Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                Log.d(TAG, "New note created: " + note.getId() + ", " + note.getNote() + ", " + note.getTimestamp());

                                // Add new item and notify adapter
                                noteList.add(0, note);
                                notesAdapter.notifyItemChanged(0);
                                toggleEmptyNotes();
                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.e(TAG, "onError: " + e.getMessage());
                                showError(e);
                            }
                        }));
    }

    /*
     * UPDATE A NOTE
     */
    private void updateNote(int noteId, final String note, final int position) {
        disposable.add(
                apiService.updateNote(noteId, note)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeWith(new DisposableCompletableObserver() {
                            @Override
                            public void onComplete() {
                                Log.d(TAG, "Note updated!");
                                Note n = noteList.get(position);
                                n.setNote(note);

                                // Update item and notify adapter
                                noteList.set(position, n);
                                notesAdapter.notifyItemChanged(position);
                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.e(TAG, "onError: " + e.getMessage());
                                showError(e);
                            }
                        }));
    }

    private void whiteNotificationBar(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = view.getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            view.setSystemUiVisibility(flags);
            getWindow().setStatusBarColor(Color.WHITE);
        }
    }

    private void toggleEmptyNotes() {
        if (noteList.size() > 0) {
            tvNoteView.setVisibility(View.GONE);
        } else {
            tvNoteView.setVisibility(View.VISIBLE);
        }
    }

    private void showError(Throwable ex) {
        String message = "";
        try {
            if (ex instanceof IOException) {
                message = "No internet connection!";
            } else if (ex instanceof HttpException) {
                HttpException error = (HttpException) ex;
                String errorBody = Objects.requireNonNull(error.response().errorBody()).string();
                JSONObject object = new JSONObject(errorBody);
                message = object.getString("error");
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }

        if (TextUtils.isEmpty(message)) {
            message = "Unknown error occured! Check logcat.";
        }

        Snackbar snackbar = Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG);

        View sbView = snackbar.getView();
        TextView textView = sbView.findViewById(android.support.design.R.id.snackbar_text);
        textView.setTextColor(Color.YELLOW);
        snackbar.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disposable.dispose();
    }
}
