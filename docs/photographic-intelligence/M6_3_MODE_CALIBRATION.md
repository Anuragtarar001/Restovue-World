# Milestone 6.3 mode calibration

Natural, Enhanced, and Extreme share analysis, semantic evidence, and masks, but use separate parameter envelopes and independently validated plans.

The supplied real photographs demonstrate scene-dependent differentiation:

| Case | Natural mean OKLab delta | Enhanced | Extreme | Result |
|---|---:|---:|---:|---|
| Sunrise | 0.0123 | 0.0284 | 0.0254 | subject recovery rises while atmosphere is protected |
| Indoor portrait | 0.0101 | 0.0120 | 0.0099 | Extreme regional proposal rejected; insufficient visible face gain |
| Wedding | 0.0173 | 0.0266 | 0.0332 | progressively stronger detected-face recovery |

The non-monotonic sunrise and indoor global deltas are intentional outcomes of independent candidate selection and quality rejection, not mislabeled modes. Extreme is allowed to converge with or be less globally different than Enhanced when stronger candidates fail a hard guard.

The complete 29-category diagnostic report records actual proposed/applied values, regions, units, and reasons for every mode. Unsupported categories are not presented as neutral decisions.

