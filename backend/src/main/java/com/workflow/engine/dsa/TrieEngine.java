package com.workflow.engine.dsa;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe in-memory Trie (prefix tree) over ticket titles.
 *
 * Complexity:
 *  - insert(title, ticketId):   O(k)      k = length of the lower-cased title
 *  - remove(title, ticketId):   O(k)
 *  - searchByPrefix(prefix):    O(p + m)  p = prefix length,
 *                                          m = number of matching ticketIds collected
 *
 * This is what backs GET /api/tickets/search?prefix=... — a real-time
 * autocomplete that never touches PostgreSQL on the read path.
 *
 * Thread-safety: a single ReentrantReadWriteLock guards the whole trie.
 * Writes (insert/remove) take the write lock; reads (search) take the
 * read lock, so concurrent searches don't block each other, only writes do.
 * We also keep a titleIndex map so remove() can be done without re-deriving
 * the exact string that was inserted for a given ticketId.
 */
@Component
public class TrieEngine {

    private final TrieNode root = new TrieNode();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /** ticketId -> the exact (lower-cased) title string currently indexed, for clean removal/reindex. */
    private final Map<Long, String> titleIndex = new ConcurrentHashMap<>();

    /** O(k) */
    public void insert(String title, Long ticketId) {
        if (title == null || ticketId == null) return;
        String normalized = title.toLowerCase(Locale.ROOT);
        lock.writeLock().lock();
        try {
            TrieNode current = root;
            for (char c : normalized.toCharArray()) {
                current = current.getChildren().computeIfAbsent(c, ch -> new TrieNode());
                current.getTicketIds().add(ticketId);
            }
            current.isEndOfWord = true;
            titleIndex.put(ticketId, normalized);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** O(k). Used when a ticket's title changes or a ticket is deleted. */
    public void remove(Long ticketId) {
        String existing = titleIndex.get(ticketId);
        if (existing == null) return;
        lock.writeLock().lock();
        try {
            TrieNode current = root;
            for (char c : existing.toCharArray()) {
                current = current.getChildren().get(c);
                if (current == null) return;
                current.getTicketIds().remove(ticketId);
            }
            titleIndex.remove(ticketId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Convenience: reindex a ticket whose title changed. O(k_old + k_new). */
    public void reindex(String newTitle, Long ticketId) {
        remove(ticketId);
        insert(newTitle, ticketId);
    }

    /**
     * Returns all ticketIds whose (lower-cased) title starts with the given prefix.
     * O(p) to walk down to the prefix node, O(m) to copy out the matching ids.
     */
    public Set<Long> searchByPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) return Collections.emptySet();
        String normalized = prefix.toLowerCase(Locale.ROOT);
        lock.readLock().lock();
        try {
            TrieNode current = root;
            for (char c : normalized.toCharArray()) {
                current = current.getChildren().get(c);
                if (current == null) {
                    return Collections.emptySet();
                }
            }
            return new LinkedHashSet<>(current.getTicketIds());
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Wipe and rebuild from scratch - used during @PostConstruct hydration. */
    public void clear() {
        lock.writeLock().lock();
        try {
            root.getChildren().clear();
            titleIndex.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
