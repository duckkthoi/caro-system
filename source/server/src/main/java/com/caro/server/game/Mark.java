package com.caro.server.game;

public enum Mark {
    X,
    O;

    public Mark opposite() {
        return this == X ? O : X;
    }

    public char symbol() {
        return this == X ? 'X' : 'O';
    }
}
