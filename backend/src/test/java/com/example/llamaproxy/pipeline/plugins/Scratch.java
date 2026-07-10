package com.example.test;

public class Scratch {
    public static void main(String[] args) {
        String duplicatedChunk = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+=-[]{}|;"; 
        System.out.println("Len 1: " + duplicatedChunk.length());

        String block1 = "This is the first highly duplicated block that should trigger a replacement because it is over fifty characters long.";
        System.out.println("Len 2: " + block1.length());
        
        String block = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        System.out.println("Len 3: " + block.length());
    }
}
