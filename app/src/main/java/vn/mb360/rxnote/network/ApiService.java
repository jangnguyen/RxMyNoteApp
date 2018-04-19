package vn.mb360.rxnote.network;

import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Single;
import retrofit2.http.DELETE;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import vn.mb360.rxnote.network.model.Note;
import vn.mb360.rxnote.network.model.User;

public interface ApiService {

    @FormUrlEncoded
    @POST("notes/user/register")
    Single<User> register(@Field("device_id") String deviceId);

    @FormUrlEncoded
    @POST("notes/new")
    Single<Note> createNote(@Field("note") String note);

    @GET("notes/all")
    Single<List<Note>> fetchAllNotes();

    @FormUrlEncoded
    @PUT("notes/{id}")
    Completable updateNote(@Path("id") int noteId, @Field("note") String note);

    @DELETE("notes/{id}")
    Completable deleteNote(@Path("id") int noteId);
}
