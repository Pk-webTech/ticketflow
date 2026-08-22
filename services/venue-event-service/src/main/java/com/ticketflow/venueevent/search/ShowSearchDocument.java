package com.ticketflow.venueevent.search;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Denormalized, flattened view of a Show for fast browse/filter queries
 * (title search, city, date range, event type, price range). Kept in sync
 * with Postgres by {@code ShowSearchSyncService} whenever a show or its
 * pricing changes — Postgres remains the source of truth.
 */
@Document(indexName = "shows")
public class ShowSearchDocument {

    @Id
    private String id; // showId as string

    @Field(type = FieldType.Keyword)
    private String eventId;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Keyword)
    private String eventType; // MOVIE | CONCERT

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private String language;

    @Field(type = FieldType.Keyword)
    private String venueId;

    @Field(type = FieldType.Text)
    private String venueName;

    @Field(type = FieldType.Keyword)
    private String city;

    @Field(type = FieldType.Date)
    private Instant showDateTime;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Double)
    private BigDecimal minPrice;

    @Field(type = FieldType.Double)
    private BigDecimal maxPrice;

    public ShowSearchDocument() {
    }

    public ShowSearchDocument(String id, String eventId, String title, String eventType, String description,
                               String language, String venueId, String venueName, String city,
                               Instant showDateTime, String status, BigDecimal minPrice, BigDecimal maxPrice) {
        this.id = id;
        this.eventId = eventId;
        this.title = title;
        this.eventType = eventType;
        this.description = description;
        this.language = language;
        this.venueId = venueId;
        this.venueName = venueName;
        this.city = city;
        this.showDateTime = showDateTime;
        this.status = status;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getVenueId() { return venueId; }
    public void setVenueId(String venueId) { this.venueId = venueId; }
    public String getVenueName() { return venueName; }
    public void setVenueName(String venueName) { this.venueName = venueName; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public Instant getShowDateTime() { return showDateTime; }
    public void setShowDateTime(Instant showDateTime) { this.showDateTime = showDateTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getMinPrice() { return minPrice; }
    public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }

    public static class Builder {
        private final ShowSearchDocument doc = new ShowSearchDocument();
        public Builder id(String v) { doc.id = v; return this; }
        public Builder eventId(String v) { doc.eventId = v; return this; }
        public Builder title(String v) { doc.title = v; return this; }
        public Builder eventType(String v) { doc.eventType = v; return this; }
        public Builder description(String v) { doc.description = v; return this; }
        public Builder language(String v) { doc.language = v; return this; }
        public Builder venueId(String v) { doc.venueId = v; return this; }
        public Builder venueName(String v) { doc.venueName = v; return this; }
        public Builder city(String v) { doc.city = v; return this; }
        public Builder showDateTime(Instant v) { doc.showDateTime = v; return this; }
        public Builder status(String v) { doc.status = v; return this; }
        public Builder minPrice(BigDecimal v) { doc.minPrice = v; return this; }
        public Builder maxPrice(BigDecimal v) { doc.maxPrice = v; return this; }
        public ShowSearchDocument build() { return doc; }
    }
}
