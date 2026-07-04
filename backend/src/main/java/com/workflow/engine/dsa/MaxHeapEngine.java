package com.workflow.engine.dsa;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Custom array-backed binary Max-Heap keyed on Ticket.priorityWeight.
 *
 * Backing store is a plain ArrayList<HeapEntry> (the classic implicit
 * binary-tree-in-an-array representation: children of index i live at
 * 2i+1 and 2i+2, parent lives at (i-1)/2). A HashMap<ticketId, index>
 * is kept alongside so we can locate any ticket's current heap slot in
 * O(1) instead of scanning the array, which is what makes increaseKey
 * run in O(log n) rather than O(n).
 *
 * Complexity summary:
 *  - insert(ticketId, weight):        O(log n)
 *  - peekMax() / extractMax():        O(1)  / O(log n)
 *  - increaseKey(ticketId, newWeight): O(log n)  (sift-up)
 *  - remove(ticketId):                 O(log n)
 *  - snapshotSortedDescending():        O(n log n) - only used to render
 *        the full ticket list sorted by priority for GET /api/tickets;
 *        this does NOT mutate the heap (it heap-sorts a copy).
 *
 * Thread-safety: a single ReentrantLock guards every mutation and read,
 * since heap operations (sift up/down, swap, index-map updates) are
 * multi-step and must be atomic with respect to each other. This is the
 * structure the 60-second @Scheduled escalation sweep in TicketService
 * calls increaseKey() against concurrently with normal request traffic.
 */
@Component
public class MaxHeapEngine {

    /** A single slot in the heap: which ticket, and its current priority weight. */
    public static class HeapEntry {
        public final Long ticketId;
        public int weight;

        HeapEntry(Long ticketId, int weight) {
            this.ticketId = ticketId;
            this.weight = weight;
        }
    }

    private final List<HeapEntry> heap = new ArrayList<>();
    private final Map<Long, Integer> indexOf = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    /** O(log n) */
    public void insert(Long ticketId, int weight) {
        lock.lock();
        try {
            if (indexOf.containsKey(ticketId)) {
                // Already present - treat as a key update instead of a duplicate insert.
                updateWeightLocked(ticketId, weight);
                return;
            }
            heap.add(new HeapEntry(ticketId, weight));
            int idx = heap.size() - 1;
            indexOf.put(ticketId, idx);
            siftUp(idx);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Re-bubbles a ticket upward after its weight has increased (e.g. an
     * escalation sweep or manual priority bump). O(log n).
     * If the new weight is actually lower, we sift down instead so the
     * heap invariant is preserved either way.
     */
    public void increaseKey(Long ticketId, int newWeight) {
        lock.lock();
        try {
            updateWeightLocked(ticketId, newWeight);
        } finally {
            lock.unlock();
        }
    }

    private void updateWeightLocked(Long ticketId, int newWeight) {
        Integer idx = indexOf.get(ticketId);
        if (idx == null) {
            // Ticket not tracked yet - treat as a fresh insert.
            heap.add(new HeapEntry(ticketId, newWeight));
            int newIdx = heap.size() - 1;
            indexOf.put(ticketId, newIdx);
            siftUp(newIdx);
            return;
        }
        HeapEntry entry = heap.get(idx);
        int old = entry.weight;
        entry.weight = newWeight;
        if (newWeight > old) {
            siftUp(idx);
        } else if (newWeight < old) {
            siftDown(idx);
        }
    }

    /** O(1) */
    public HeapEntry peekMax() {
        lock.lock();
        try {
            return heap.isEmpty() ? null : heap.get(0);
        } finally {
            lock.unlock();
        }
    }

    /** O(log n) */
    public HeapEntry extractMax() {
        lock.lock();
        try {
            if (heap.isEmpty()) return null;
            HeapEntry max = heap.get(0);
            HeapEntry last = heap.remove(heap.size() - 1);
            indexOf.remove(max.ticketId);
            if (!heap.isEmpty() && !max.ticketId.equals(last.ticketId)) {
                heap.set(0, last);
                indexOf.put(last.ticketId, 0);
                siftDown(0);
            }
            return max;
        } finally {
            lock.unlock();
        }
    }

    /** O(log n). Used when a ticket is resolved/deleted and must leave the active heap. */
    public void remove(Long ticketId) {
        lock.lock();
        try {
            Integer idx = indexOf.get(ticketId);
            if (idx == null) return;
            int lastIdx = heap.size() - 1;
            HeapEntry last = heap.remove(lastIdx);
            indexOf.remove(ticketId);
            if (idx != lastIdx) {
                heap.set(idx, last);
                indexOf.put(last.ticketId, idx);
                siftDown(idx);
                siftUp(idx);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * O(n log n). Non-destructive: heap-sorts a copy of the current entries
     * descending by weight, for rendering GET /api/tickets in priority order.
     */
    public List<HeapEntry> snapshotSortedDescending() {
        lock.lock();
        try {
            List<HeapEntry> copy = new ArrayList<>(heap.size());
            for (HeapEntry e : heap) {
                copy.add(new HeapEntry(e.ticketId, e.weight));
            }
            copy.sort((a, b) -> Integer.compare(b.weight, a.weight));
            return copy;
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            heap.clear();
            indexOf.clear();
        } finally {
            lock.unlock();
        }
    }

    // ---- internal helpers: classic array-heap sift operations, both O(log n) ----

    private void siftUp(int idx) {
        while (idx > 0) {
            int parent = (idx - 1) / 2;
            if (heap.get(idx).weight <= heap.get(parent).weight) break;
            swap(idx, parent);
            idx = parent;
        }
    }

    private void siftDown(int idx) {
        int size = heap.size();
        while (true) {
            int left = 2 * idx + 1;
            int right = 2 * idx + 2;
            int largest = idx;
            if (left < size && heap.get(left).weight > heap.get(largest).weight) {
                largest = left;
            }
            if (right < size && heap.get(right).weight > heap.get(largest).weight) {
                largest = right;
            }
            if (largest == idx) break;
            swap(idx, largest);
            idx = largest;
        }
    }

    private void swap(int i, int j) {
        HeapEntry tmp = heap.get(i);
        heap.set(i, heap.get(j));
        heap.set(j, tmp);
        indexOf.put(heap.get(i).ticketId, i);
        indexOf.put(heap.get(j).ticketId, j);
    }
}
