package com.workflow.engine.dsa;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * A single node of the Trie.
 *
 * children: O(1) average lookup per character via ConcurrentHashMap, so a
 *           single insert/search over a word of length k is O(k).
 * ticketIds: every ticket whose title passes through this node - lets us
 *           answer "all tickets with this prefix" without re-walking titles.
 *           ConcurrentSkipListSet keeps it thread-safe and naturally ordered.
 */
public class TrieNode {

    final Map<Character, TrieNode> children = new ConcurrentHashMap<>();
    final Set<Long> ticketIds = new ConcurrentSkipListSet<>();
    volatile boolean isEndOfWord = false;

    public Map<Character, TrieNode> getChildren() {
        return children;
    }

    public Set<Long> getTicketIds() {
        return ticketIds;
    }

    public boolean isEndOfWord() {
        return isEndOfWord;
    }
}
