package com.library.domain;

import java.util.List;
import java.util.UUID;

public final class Book {
    private final UUID id;
    private final String isbn;
    private final String title;
    private final List<String> authors;

    public Book(String isbn, String title, List<String> authors) {
        this.id = UUID.randomUUID();
        this.isbn = isbn;
        this.title = title;
        this.authors = List.copyOf(authors);
    }
    public UUID id() { return id; }
    public String isbn() { return isbn; }
    public String title() { return title; }
    public List<String> authors() { return authors; }
}
