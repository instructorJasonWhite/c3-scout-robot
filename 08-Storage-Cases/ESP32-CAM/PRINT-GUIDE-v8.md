# Scout ESP32-CAM case v8: thin shell with 5 mm bevel

The ESP32-CAM revision returns the closed shell to its original nominal 107.6 x 155.6 x 100.6 mm envelope, excluding hinge projections. Broad walls, floor and roof are 2.8 mm before the shallow decorative recesses. The 5 mm 45-degree outside bevel has 1 mm softened transitions. A matching inside bevel (3.3598 mm setback) lets the inside corners follow the exterior, rather than filling the shell to v7's 7.8 mm flat thickness.

The original front wall is retained locally across the latch mounting region (Y -1 to 33 mm), and the original captive hinge roots are retained. These functional lands interrupt the bevel locally so it does not weaken the latch attachment or hinge. Original shallow graphics and grip recesses remain. The minimum wall in a shallow decorative recess can be below the nominal 2.8 mm, as in v6.

The four original Scout support positions and heights remain unchanged. Side guides are 5 mm taller than v6, and the two rounded rear locators from v7 flank the tail. The 0.20 mm-per-face vertical dovetail, separate flat spring and front push-release mechanism retain the v6 geometry. The separate latch is the ESP32-CAM length.

## Files and printing

Use Scout-ESP32-CAM-Case-v8.f3d for Fusion editing. The complete STEP/STL has the captive box pair and separate flat latch. The -Box.stl and -Latch.stl are separate alternatives. Do not load complete and separate meshes together, and never independently arrange the base and lid.

Use the v8 P1S Studio project and fresh slice. Older v6/v7 sliced plates are not valid for v8. The supplied orientation puts the captive pair on the bed with its intended hinge relationship and lays the spring on its broad face. Setup: P1S 0.4 mm, PLA Basic filament 1, textured PEI, 0.20 mm layers, four walls, 15% grid, five top and three bottom layers, no supports/brim/prime tower. Use the same calibrated material as the successful fit samples.

## Assembly and limits

Cool the print before gently freeing the captive hinges. With the lid open, align the thick dovetail foot at the internal entry channel and slide it toward the lid roof until seated. Hold the thick foot rather than levering the flexible blade. Close gently for the first test. Push the catch inward through the front opening, lift the lid through the first few degrees and release.

The delivered geometry is checked against the full 712-body assembled ESP32-CAM Scout reference, through latch insertion and sampled opening motion. Rigid checks do not simulate spring bending, printed dimensional error or fatigue. Check actual wiring and straps and gently cycle the printed latch before loading Scout. Earlier v6/v7 files are preserved. User parameters do not drive every inherited feature; the native Fusion archive contains editable feature and BRep history.

## Verified Studio result

Estimated 5h 25m 29s, 266.18 g PLA, 374 layers. Final meshes match the Studio project; native archive reopening, flat thickness sampling, retained latch attachment and sliced checksum checks passed. No print has been sent.

Only PLA filament 1 prints. Unused inherited filament profiles can show a zero-flushing-volume warning.
