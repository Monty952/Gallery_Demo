package com.contacts.app.phone.calls.gallerydemo.model;

import androidx.annotation.Nullable;

import java.util.Objects;

public class CallLogItem {
    private final long id;
    @Nullable
    private final String name;
    private final String number;
    private final int type;
    private final long date;
    private final long duration;
    @Nullable
    private final String lookupUri;

    public CallLogItem(long id,
                       @Nullable String name,
                       String number,
                       int type,
                       long date,
                       long duration,
                       @Nullable String lookupUri) {
        this.id = id;
        this.name = name;
        this.number = number != null ? number : "";
        this.type = type;
        this.date = date;
        this.duration = duration;
        this.lookupUri = lookupUri;
    }

    public long getId() {
        return id;
    }

    @Nullable
    public String getName() {
        return name;
    }

    public String getNumber() {
        return number;
    }

    public int getType() {
        return type;
    }

    public long getDate() {
        return date;
    }

    public long getDuration() {
        return duration;
    }

    @Nullable
    public String getLookupUri() {
        return lookupUri;
    }

    /**
     * Checks if two items represent the exact same database record.
     * Useful for RecyclerView DiffUtil comparisons.
     */
    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CallLogItem that = (CallLogItem) o;
        return id == that.id &&
                type == that.type &&
                date == that.date &&
                duration == that.duration &&
                Objects.equals(name,that.name) &&
                Objects.equals(number,that.number);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, number, type, date, duration);
    }
}
