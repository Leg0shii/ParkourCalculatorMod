# Saved candidate combinations and retention

Generated from the archived event-priority run, without rerunning IPOPT. Full exact programs and parent IDs are in candidate-programs.json.gz and candidate-ledger.csv. `J` lists jump input ticks; `R` lists grounded inputs with no jump. Contacts show resulting state ticks. Repeated ordinary B1 ground-contact entries before the first jump are compressed out of this table only. `F` means the outer scalar/support/corridor/scene checks accepted the returned fixed-clock witness; `U` is unknown, not proven infeasible. Calls are fixed-clock + spatial-probe numerical calls. No scalar-facing values are committed.

## State 10

| Candidate | J | R | Nontrivial contacts | Clock | Progress | Goal time | Calls | Retention |
| --- | --- | --- | --- | --- | ---: | ---: | --- | --- |
| H10-01 | 0 | - | - | F | 2.39265 | 21 | 1+4 | markedly_worse |
| H10-02 | 1 | 0 | - | F | 2.57242 | 22 | 1+4 | markedly_worse |
| H10-03 | - | 0..1 | - | U | ? | ? | 1+0 | retained |
| H10-04 | 2 | 0..1 | - | F | 2.66033 | 23 | 1+6 | markedly_worse |
| H10-05 | - | 0..2 | - | U | ? | ? | 1+0 | unknown_resource_cap |
| H10-06 | 3 | 0..2 | - | F | 2.70156 | 24 | 1+6 | markedly_worse |
| H10-07 | - | 0..3 | - | U | ? | ? | 1+0 | unknown_resource_cap |
| H10-08 | 4 | 0..3 | - | F | 2.71937 | ? | 1+6 | retained |
| H10-09 | - | 0..4 | - | U | ? | ? | 5+0 | unknown_resource_cap |
| H10-10 | 5 | 0..4 | - | F | 2.72562 | ? | 1+6 | retained |
| H10-11 | - | 0..5 | - | U | ? | ? | 5+0 | unknown_resource_cap |
| H10-12 | 6 | 0..5 | - | F | 2.72627 | ? | 1+6 | retained |
| H10-13 | - | 0..6 | - | U | ? | ? | 5+0 | unknown_resource_cap |
| H10-14 | 7 | 0..6 | - | F | 2.72428 | ? | 1+6 | retained |
| H10-15 | - | 0..7 | - | U | ? | ? | 5+0 | unknown_resource_cap |
| H10-16 | 8 | 0..7 | - | F | 2.72106 | 17 | 1+6 | retained |
| H10-17 | - | 0..8 | - | F | 1.95540 | ? | 6+0 | retained |
| H10-18 | 9 | 0..8 | - | F | 2.71728 | 18 | 1+6 | retained |
| H10-19 | - | 0..9 | - | F | 2.51988 | 19 | 1+4 | markedly_worse |
| H10-20 | - | 0..9 | - | F | 2.30956 | ? | 6+0 | resource_cap |

Retained, in next-window parent order: H10-16, H10-18, H10-03, H10-08, H10-10, H10-12, H10-14, H10-17.

## State 28

| Candidate | J | R | Nontrivial contacts | Clock | Progress | Goal time | Calls | Retention |
| --- | --- | --- | --- | --- | ---: | ---: | --- | --- |
| H28-01 | 8,17,26 | 0..7 | B2@17,B3@26 | U | ? | ? | 1+1 | retained |
| H28-02 | 9,18,27 | 0..8 | B2@18,B3@27 | F | 8.83960 | 27 | 2+2 | retained |
| H28-03 | - | 0..1 | - | U | ? | ? | 1+0 | unknown_resource_cap |
| H28-04 | 4 | 0..3 | - | U | ? | ? | 6+0 | unknown_resource_cap |
| H28-05 | 5 | 0..4 | - | U | ? | ? | 6+0 | unknown_resource_cap |
| H28-06 | 6 | 0..5 | - | U | ? | ? | 6+0 | unknown_resource_cap |
| H28-07 | 7 | 0..6 | - | U | ? | ? | 6+0 | unknown_resource_cap |
| H28-08 | - | 0..8 | - | U | ? | ? | 6+0 | unknown_resource_cap |
| H28-09 | 8,17 | 0..7,26 | B2@17,B3@26 | U | ? | ? | 1+1 | unknown_resource_cap |
| H28-10 | 9,18 | 0..8,27 | B2@18,B3@27 | U | ? | 27 | 2+2 | retained |
| H28-11 | 4,16,25 | 0..3 | B1@16,B2@25 | F | 7.58171 | 34 | 1+6 | markedly_worse |
| H28-12 | 5,17,26 | 0..4 | B1@17,B2@26 | F | 7.22916 | ? | 1+6 | retained |
| H28-13 | 6,18,27 | 0..5 | B1@18,B2@27 | F | 6.86913 | ? | 1+6 | retained |
| H28-14 | 7,19 | 0..6 | B1@19,B2@28 | F | 6.25458 | ? | 1+6 | resource_cap |
| H28-15 | 8,17,27 | 0..7,26 | B2@17,B3@26,B3@27 | U | ? | ? | 1+2 | unknown_resource_cap |
| H28-16 | 9,18 | 0..8,27 | B2@18,B3@27,B3@28 | F | 8.64220 | 27 | 2+4 | retained |
| H28-17 | 4,16 | 0..3,25 | B1@16,B2@25 | F | 7.17845 | ? | 1+0 | retained |
| H28-18 | 5,17 | 0..4,26 | B1@17,B2@26 | F | 6.92398 | ? | 1+0 | retained |
| H28-19 | 6,18 | 0..5,27 | B1@18,B2@27 | F | 6.67173 | ? | 1+0 | resource_cap |
| H28-20 | 7,19 | 0..6 | B1@19 | U | ? | ? | 6+6 | unknown_resource_cap |

Retained, in next-window parent order: H28-02, H28-10, H28-16, H28-01, H28-12, H28-13, H28-17, H28-18.

## State 44

| Candidate | J | R | Nontrivial contacts | Clock | Progress | Goal time | Calls | Retention |
| --- | --- | --- | --- | --- | ---: | ---: | --- | --- |
| H44-01 | 9,18,27,37 | 0..8 | B2@18,B3@27,B4@37 | U | ? | ? | 2+1 | retained |
| H44-02 | 9,18 | 0..8,27 | B2@18,B3@27 | U | ? | ? | 0+0 | unknown_resource_cap |
| H44-03 | 9,18,28,38 | 0..8,27 | B2@18,B3@27,B3@28,B4@38 | U | ? | ? | 1+1 | unknown_resource_cap |
| H44-04 | 8,17,26,36 | 0..7 | B2@17,B3@26,B4@36 | U | ? | ? | 1+1 | unknown_resource_cap |
| H44-05 | 5,17,26,35 | 0..4 | B1@17,B2@26,B3@35 | F | 12.95223 | 45 | 3+6 | retained |
| H44-06 | 6,18,27,36 | 0..5 | B1@18,B2@27,B3@36 | F | 12.62855 | 46 | 3+6 | markedly_worse |
| H44-07 | 4,16 | 0..3,25 | B1@16,B2@25 | U | ? | ? | 6+0 | unknown_resource_cap |
| H44-08 | 5,17 | 0..4,26 | B1@17,B2@26 | U | ? | ? | 6+0 | unknown_resource_cap |
| H44-09 | 9,18,27 | 0..8,37 | B2@18,B3@27,B4@37 | U | ? | ? | 2+1 | unknown_resource_cap |
| H44-10 | 9,18,28 | 0..8,27,38 | B2@18,B3@27,B3@28,B4@38 | U | ? | ? | 1+1 | unknown_resource_cap |
| H44-11 | 8,17,26 | 0..7,36 | B2@17,B3@26,B4@36 | U | ? | ? | 1+1 | unknown_resource_cap |
| H44-12 | 5,17,26,35 | 0..4 | B1@17,B2@26,B3@35 | F | 12.95223 | 45 | 3+6 | retained |
| H44-13 | 6,18,27 | 0..5,36 | B1@18,B2@27,B3@36 | U | ? | ? | 6+0 | unknown_resource_cap |
| H44-14 | 9,18,27,38 | 0..8,37 | B2@18,B3@27,B4@37,B4@38 | U | ? | ? | 2+2 | unknown_resource_cap |
| H44-15 | 9,18,28,39 | 0..8,27,38 | B2@18,B3@27,B3@28,B4@38,B4@39 | U | ? | ? | 1+2 | unknown_resource_cap |
| H44-16 | 8,17,26,37 | 0..7,36 | B2@17,B3@26,B4@36,B4@37 | U | ? | ? | 1+2 | unknown_resource_cap |
| H44-17 | 5,17,26 | 0..4,35 | B1@17,B2@26,B3@35 | U | ? | ? | 6+0 | unknown_resource_cap |
| H44-18 | 6,18,27,37 | 0..5,36 | B1@18,B2@27,B3@36,B3@37 | F | 12.50880 | 47 | 2+6 | markedly_worse |
| H44-19 | 9,18,27 | 0..8,37..38 | B2@18,B3@27,B4@37,B4@38 | U | ? | ? | 2+2 | unknown_resource_cap |
| H44-20 | 9,18,28 | 0..8,27,38..39 | B2@18,B3@27,B3@28,B4@38,B4@39 | U | ? | ? | 1+2 | unknown_resource_cap |

Retained, in next-window parent order: H44-05, H44-12, H44-01.

## State 55

| Candidate | J | R | Nontrivial contacts | Clock | Progress | Goal time | Calls | Retention |
| --- | --- | --- | --- | --- | ---: | ---: | --- | --- |
| H55-01 | 5,17,26,35,45 | 0..4 | B1@17,B2@26,B3@35,B4@45 | F | 6.02307 | ? | 6+6 | resource_cap |
| H55-02 | 5,17,26,35,45 | 0..4 | B1@17,B2@26,B3@35,B4@45 | F | 6.02307 | ? | 6+6 | resource_cap |
| H55-03 | 9,18,27,37,52 | 0..8 | B2@18,B3@27,B4@37,B5@52 | U | ? | ? | 2+1 | retained |
| H55-04 | 5,17,26,35 | 0..4,45 | B1@17,B2@26,B3@35,B4@45 | U | ? | ? | 6+0 | unknown_resource_cap |
| H55-05 | 5,17,26,35 | 0..4,45 | B1@17,B2@26,B3@35,B4@45 | U | ? | ? | 6+0 | unknown_resource_cap |
| H55-06 | 9,18,27,37 | 0..8,52 | B2@18,B3@27,B4@37,B5@52 | U | ? | ? | 2+1 | unknown_resource_cap |
| H55-07 | 5,17,26,35,46 | 0..4,45 | B1@17,B2@26,B3@35,B4@45,B4@46 | F | 9.06668 | ? | 6+6 | retained |
| H55-08 | 5,17,26,35,46 | 0..4,45 | B1@17,B2@26,B3@35,B4@45,B4@46 | F | 9.06668 | ? | 6+6 | retained |
| H55-09 | 9,18,27,37,53 | 0..8,52 | B2@18,B3@27,B4@37,B5@52,B5@53 | U | ? | ? | 2+2 | unknown_resource_cap |
| H55-10 | 5,17,26,35 | 0..4,45..46 | B1@17,B2@26,B3@35,B4@45,B4@46 | F | 8.23488 | ? | 6+0 | retained |
| H55-11 | 5,17,26,35 | 0..4,45..46 | B1@17,B2@26,B3@35,B4@45,B4@46 | F | 8.23488 | ? | 6+0 | resource_cap |
| H55-12 | 9,18,27,37 | 0..8,52..53 | B2@18,B3@27,B4@37,B5@52,B5@53 | U | ? | ? | 2+2 | unknown_resource_cap |
| H55-13 | 5,17,26,35,47 | 0..4,45..46 | B1@17,B2@26,B3@35,B4@45,B4@46,B4@47 | F | 8.66912 | ? | 6+6 | retained |
| H55-14 | 5,17,26,35,47 | 0..4,45..46 | B1@17,B2@26,B3@35,B4@45,B4@46,B4@47 | F | 8.66912 | ? | 6+6 | retained |
| H55-15 | 9,18,27,37,54 | 0..8,52..53 | B2@18,B3@27,B4@37,B5@52,B5@53,B5@54 | U | ? | ? | 2+3 | unknown_resource_cap |
| H55-16 | 5,17,26,35 | 0..4,45..47 | B1@17,B2@26,B3@35,B4@45,B4@46,B4@47 | F | 7.89302 | ? | 6+0 | resource_cap |
| H55-17 | 5,17,26,35 | 0..4,45..47 | B1@17,B2@26,B3@35,B4@45,B4@46,B4@47 | F | 7.89302 | ? | 6+0 | resource_cap |
| H55-18 | 9,18,27,37 | 0..8,52..54 | B2@18,B3@27,B4@37,B5@52,B5@53,B5@54 | U | ? | ? | 2+3 | unknown_resource_cap |
| H55-19 | 5,17,26,35,48 | 0..4,45..47 | B1@17,B2@26,B3@35,B4@45,B4@46,B4@47,B4@48 | F | 8.33426 | ? | 6+5 | retained |
| H55-20 | 5,17,26,35,48 | 0..4,45..47 | B1@17,B2@26,B3@35,B4@45,B4@46,B4@47,B4@48 | F | 8.33426 | ? | 6+5 | retained |

Retained, in next-window parent order: H55-03, H55-07, H55-08, H55-10, H55-13, H55-14, H55-19, H55-20.
