package com.metrovoc.phonon.server;

/** Audio being added right now. Percent is -1 until it's known. */
public record LibraryJob(String label, int percent) {}
