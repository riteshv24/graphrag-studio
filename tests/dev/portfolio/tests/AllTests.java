package dev.portfolio.tests;

import dev.portfolio.foundation.*;
import dev.portfolio.graphrag.Product;

public final class AllTests {
    public static void main(String[] args) throws Exception {
        FoundationTests.run(() -> new Product(new MemoryStore(), TestSupport.offlineModel()));
        DomainTests.run();
        TestSupport.finish();
    }
}
