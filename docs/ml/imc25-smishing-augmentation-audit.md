# IMC25 public-report smishing augmentation audit

## Source and permission

The source is the `final_dataset_output.csv` artifact published by the authors
of *Fishing for Smishing: Understanding SMS Phishing Infrastructure and
Strategies by Mining Public User Reports* (IMC 2025). The repository labels
the artifact under Creative Commons Attribution 4.0 International. Cite the
paper and dataset if this material is used in a released model:

- Dataset and license: <https://github.com/reportsmishing/Smishing-Dataset-IMC25>
- Paper record: <https://discovery.ucl.ac.uk/id/eprint/10214522/>

## Local audit

The CSV was downloaded to temporary storage for the experiment; it was not
copied into the project repository. SHA-256:
`1bbd1e9e82c3ea023112207b80da268a5c4a07d2353c2b0898360ab037fa9a64`.

The source file has 33,869 rows and 12 metadata/text columns. It contains
25,329 distinct message texts before cross-corpus deduplication, including
8,540 repeated rows. Rows are public user reports labeled as smishing; this
is positive-only data and is not representative ordinary inbox traffic. The
CSV reports 175 Hindi rows and 3,729 India-origin rows before filtering.

For the augmentation run, only rows identified as India-origin (`IND`) or
Hindi were eligible. Normalized exact-text checks removed repeated examples
and any overlap with the existing train, validation, or test splits. The
result added 2,481 unique scam examples to training only, removed 1,374
duplicate eligible rows, found zero exact split overlaps, and excluded 30,014
non-target rows. Validation, test, and the separate authored challenge set
were not augmented.

## Limits

This dataset cannot teach a classifier what benign Indian messages look like.
The published corpus has a reporting-selection bias, and its Hindi portion is
small. Its messages should be treated as supplementary scam examples only;
they are not an independent evaluation set. The experiment must be judged on
the existing held-out sets and frozen challenge, with false positives given
equal attention to scam recall.
