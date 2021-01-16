package com.example.actors.entity;

import com.example.actors.GoogleCalendarSource;
import com.example.actors.LocationToPointMapper;

import java.util.List;

public class ResponseItem {
    public String id;
    public GoogleCalendarSource.GoogleCalendarEventItem item;
    public LocationToPointMapper.GeocodingLocation location;
    public List<DisasterEntity> disasterEntities;

    public ResponseItem(String id, GoogleCalendarSource.GoogleCalendarEventItem item) {
        this.id = id;
        this.item = item;
    }

    public ResponseItem(String id, GoogleCalendarSource.GoogleCalendarEventItem item, LocationToPointMapper.GeocodingLocation location) {
        this.id = id;
        this.item = item;
        this.location = location;
    }

    public ResponseItem(String id, GoogleCalendarSource.GoogleCalendarEventItem item, LocationToPointMapper.GeocodingLocation location, List<DisasterEntity> disasterEntities) {
        this.id = id;
        this.item = item;
        this.location = location;
        this.disasterEntities = disasterEntities;
    }
}
