# Tone-curve calibration

Restovue manual Gamma +38 stores gamma `1.38` and applies encoded-channel `c^(1/1.38)` globally. This is effective at lifting midtones but necessarily changes the entire scene.

Smart Magic uses a different, explicit regional operation:

`Y_out = Y_in^(1 / (1 + gamma_delta))`

It operates on linear luminance, preserves chromatic ratios, uses shadow-reliability and highlight guards, and composites through the soft person mask. When a reliable face exists, a smooth face weighting prioritizes darker facial midtones without treating the box as a skin mask.

For the sunrise image:

- Previous Extreme face luminance: 0.09154, slightly below the 0.09175 original.
- Natural: 0.10570 (+15.2%).
- Enhanced: 0.11347 (+23.7%).
- Extreme: 0.12012 (+30.9%).

The Extreme face lift is comparable in purpose to manual gamma but avoids global sunrise/mist alteration. The exact wedding photograph remains unavailable, so no claim is made that the automatic result matches its manual Gamma +38 edit.

