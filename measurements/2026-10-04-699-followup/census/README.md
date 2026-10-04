# Separate #699 collection census

This read-only follow-up passed on a second worker with the same native #699 jar and frozen client snapshot. It is not part of the matched timing cohort and supplies no timing comparison.

The actual represented-tab counting pass sees 243 list entries: 241 stock CreativeModeTab entries and two MekanismCreativeTab entries. A separate census takes the identity union of the two public vanilla tab-list views and sees 241 unique non-search objects: 239 stock and two Mekanism objects. Both counts are retained rather than conflated.

For all 482 inspected getter/object pairs:

- The getter resolves to CreativeModeTab, including both Mekanism objects.
- The returned collection is exactly ObjectLinkedOpenCustomHashSet.
- Its strategy is the identical object returned by vanilla's createTypeAndTagSet factory.

The census checks a getter's declaring class before invoking it and skips overrides, so it does not add calls to an arbitrary custom getter. The factory reference set's exact class was not separately logged. The existing native guard still falls back on the Mekanism subclass; this follow-up does not change that source or demonstrate an indexed-search speedup.

A potential narrow revision would permit a subclass only when the collection getters remain inherited from CreativeModeTab, caching the classification per class. Actual getter overrides would retain the original loop. Collection class/strategy guards would stay in place. That proposal still needs source review, regression checks and a new native client measurement before any benefit can be claimed.

The snapshot manifest matches the timing cohort. All 246 source-world payload files match too; the worker has one extra mirror-ownership marker. The client has 512 enabled mod jars. The census probe compiled on a worker and the client completed without a crash. The CSV contains only technical class/getter/type metadata, counts and strategy-identity results; no item contents, world data or raw logs are included.
