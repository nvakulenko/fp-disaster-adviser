package com.example.actors.entity;

import com.example.actors.DisasterNasaSource;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.List;

public class DisasterEntity {
    @BsonProperty("_id")
    @BsonId
    private ObjectId _id;
    @BsonProperty("nasaId")
    private String nasaId;
    @BsonProperty("title")
    private String title;
    @BsonProperty("closed")
    private String closed;
    private Instant date;
    private List<CategoryEntity> categories;
    private LocationEntity location;

    public DisasterEntity() {
    }

    public DisasterEntity(ObjectId _id) {
        this._id = _id;
    }
    public void setId(ObjectId _id) {
        this._id = _id;
    }
    public ObjectId getId() {
        return _id;
    }
    public String getNasaId() {
        return nasaId;
    }
    public void setNasaId(String nasaId) {
        this.nasaId = nasaId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getClosed() {
        return closed;
    }

    public void setClosed(String closed) {
        this.closed = closed;
    }

    public LocationEntity getLocation() {
        return location;
    }

    public void setLocation(LocationEntity location) {
        this.location = location;
    }

    public Instant getDate() {
        return date;
    }

    public void setDate(Instant date) {
        this.date = date;
    }

    public List<CategoryEntity> getCategories() {
        return categories;
    }

    public void setCategories(List<CategoryEntity> categories) {
        this.categories = categories;
    }
}
