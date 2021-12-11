-- // CB-14399: Modify unique CRN constraint to unique (CRN, terminated) constraint.
-- Migration SQL that makes the change goes here.
ALTER TABLE stack DROP CONSTRAINT IF EXISTS stack_crn_uq;
ALTER TABLE stack ADD CONSTRAINT stack_crn_terminated_uq UNIQUE (resourceCrn, terminated);

-- //@UNDO
-- SQL to undo the change goes here.
ALTER TABLE stack DROP CONSTRAINT IF EXISTS stack_crn_terminated_uq;
ALTER TABLE stack ADD CONSTRAINT stack_crn_uq UNIQUE (resourceCrn);
