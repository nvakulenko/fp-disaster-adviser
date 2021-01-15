package com.example.actors.entity;


import java.math.BigDecimal;

public class LocationEntity {
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    String type;

    public BigDecimal[] getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(BigDecimal[] coordinates) {
        this.coordinates = coordinates;
    }

    BigDecimal[] coordinates;
}
