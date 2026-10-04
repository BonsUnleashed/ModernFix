# Native ModernFix full-client measurements

The native changes were tested separately in a frozen, real modpack client. The results show a storage/construction tradeoff for #697, natural memo hits for #698, and an eligibility fallback for the current #699. They do not establish an overall reload or FPS improvement.

The four-process comparison for #697 ran baseline → candidate → candidate → baseline, with three resource reloads in each process. #698 and #699 each ran once afterward with the same three-reload scene. Initial loads remain in the CSVs; the summary below uses only completed reloads. Repeated reloads within one process are not independent process replicates.

## #697: real bake handlers and backing storage

Each ModifyBakingResult helper contains 1,151,485 actual model locations from the loaded registry. Its immediate set plus backing arrays is **25,165,928 bytes with fastutil and 12,994,616 bytes with ImmutableSet**, a 48.36% reduction. This excludes the shared ResourceLocation objects and does not imply those bytes remain live after the bake event. The warm BakingCompleted helpers have 1,151,737 locations and the same rounded reduction.

Median across six reloads per arm; seconds:

| Measured work | Baseline | #697 |
|---|---:|---:|
| ModifyBakingResult: helper construction | 0.579 | 0.968 |
| ModifyBakingResult: all real container dispatches | 2.817 | 2.236 |
| BakingCompleted: helper construction | 0.545 | 0.884 |
| BakingCompleted: all real container dispatches | 0.196 | 0.195 |
| Both events: construction + dispatch sums | 4.145 | 4.280 |

Actual ModifyBakingResult handler medians, in milliseconds (six reloads each):

| Mod container | Baseline | #697 |
|---|---:|---:|
| dimdoors | 571.0 | 632.3 |
| create | 561.4 | 508.1 |
| alexscaves | 425.0 | 298.5 |
| mekanism | 381.0 | 306.4 |
| alexsmobs | 332.1 | 188.7 |
| refinedstorage | 193.5 | 81.9 |

The handler benefit is offset by extra construction work in this workload. Median constructor allocation changes, in MiB:

| Event | Baseline | #697 |
|---|---:|---:|
| ModifyBakingResult | 210.96 | 227.75 |
| BakingCompleted | 200.20 | 216.99 |

The candidate still builds the original fastutil set before making the immutable copy, so the smaller backing storage has an up-front temporary allocation cost. Both-event construction plus dispatch allocation totals are 3242.89 MiB baseline and 3277.09 MiB candidate. These are allocations during the work, not retained heap.

## #698: natural cube-memo activation

With compact entity models explicitly enabled in **both** arms, the candidate has 65,789 per-definition memo hits out of 94,424 CubeDefinition calls before joining the world. Every subsequent resource reload has **66,076 hits out of 91,852 calls (71.9%)**; the other 25,776 calls take the existing shared-cache path. The fixture never repeatedly calls bake methods to create these hits.

The 30-second equipped-player rendering window rendered 3,433 frames but made zero CubeDefinition calls in either path. Therefore this scene provides no evidence of an FPS benefit. Whole-resource reload timings overlap the baseline range; there is no established end-to-end improvement from this single candidate process.

## #699: current guard falls back

Ordinary typing in Botania’s installed search tab invokes represented-tab counting. The real tab list includes 241 stock CreativeModeTab objects and two MekanismCreativeTab objects. The submitted exact-class eligibility guard therefore returns to the original loop before constructing an index. Search timings here do not measure a benefit from the index. A separate collection/strategy census is needed before considering a wider eligibility rule; this cohort remains unchanged.

## Whole resource reloads

Elapsed time through Minecraft’s real resource-reload future, including its normal work and fixture hooks:

| Arm | Reloads | Median seconds | Range seconds |
|---|---:|---:|---:|
| baseline | 6 | 29.348 | 28.336–34.288 |
| 697 | 6 | 29.986 | 27.757–35.459 |
| 698 | 3 | 29.458 | 28.855–32.750 |
| 699 | 3 | 30.271 | 28.543–33.569 |

These small samples vary with JIT, GC and background activity. No whole-reload speedup is claimed.

## Setup and limits

All six processes completed on the same Ryzen 9 5900X / RTX 3080 Windows machine using Eclipse Adoptium Java 21.0.12.1, G1, Xms2G/Xmx10G and ActiveProcessorCount=8. Native sources start at `aa3fff5971affbce1ca0501960158d41c11349b3`. The frozen snapshot contains 511 top-level mod jars; replacing ModernFix and adding the measurement probe results in 512 enabled fixture jars. Instrumentation observes 553 distinct mod-container dispatch IDs per event. These counts describe different things.

The snapshot contains Bons and Furious 1.0.26, predating these downstream changes, and its 19 external resource packs. The three counterpart properties are false. Shaders remain enabled (Complementary Unbound, EuphoriaPatches and ACLight). The copied source world, snapshot and scripted scene hashes match across all arms. No managed instance or real world was changed.

The window is minimized/background with a 120 FPS limit throughout; render counts establish activity, not comparable foreground FPS. The fixture collects one boxed frame-time sample per frame. There are no concurrent local game/build JVMs at launch, but the workstation is not fully idle: VS Code/browser activity and high two-second CPU samples just after previous clients exit are recorded. The measurements should not be treated as quiet-machine precision benchmarks.

The original snapshot emits nonfatal resource warnings, including 12 Ice and Fire missing Tabula-stream exceptions in each completed process. All arms complete their reloads and scene. Whole-client heap samples and GC counts are supplied for context, but no heap saving is attributed to one patch from those noisy samples.

The CSVs include each actual container dispatch, constructor, footprint, activation count and reload. The fixture folder contains the exact native patches, measurement diffs and helper sources. `reproduction.md` describes the method and the instrumentation limits. `parser-recoveries.json` records any intact logger-interleaved timing fragments that had to be joined. No raw logs, usernames, world names or local paths are included.
