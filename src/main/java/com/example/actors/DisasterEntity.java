package com.example.actors;

import java.util.List;

public class DisasterEntity {
    private Integer _id;
    private String nasaId;
    private String title;
    private String closed;
    private List<DisasterNasaSource.NasaDisasterCategories> categories;
    private List<DisasterNasaSource.NasaDisasterGeometry> geometry;

    public DisasterEntity() {
    }
    public DisasterEntity(Integer _id) {
        this._id = _id;
    }
    public void setId(Integer _id) {
        this._id = _id;
    }
    public Integer getId() {
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

    public List<DisasterNasaSource.NasaDisasterCategories> getCategories() {
        return categories;
    }

    public void setCategories(List<DisasterNasaSource.NasaDisasterCategories> categories) {
        this.categories = categories;
    }

    public List<DisasterNasaSource.NasaDisasterGeometry> getGeometry() {
        return geometry;
    }

    public void setGeometry(List<DisasterNasaSource.NasaDisasterGeometry> geometry) {
        this.geometry = geometry;
    }
}
