/*
* Copyright (c) 1997 - 2015
* Actelion Pharmaceuticals Ltd.
* Gewerbestrasse 16
* CH-4123 Allschwil, Switzerland
*
* All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
* 1. Redistributions of source code must retain the above copyright notice, this
*    list of conditions and the following disclaimer.
* 2. Redistributions in binary form must reproduce the above copyright notice,
*    this list of conditions and the following disclaimer in the documentation
*    and/or other materials provided with the distribution.
* 3. Neither the name of the the copyright holder nor the
*    names of its contributors may be used to endorse or promote products
*    derived from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
* ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
* WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
* ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
* (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
* LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
* ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
* (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
* SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*
*/

package com.actelion.research.chem;

import java.io.*;

public class StereoMolecule extends ExtendedMolecule {
    static final long serialVersionUID = 0x2006CAFE;
    
    public static final String VALIDATION_ERROR_ESR_CENTER_UNKNOWN = "Members of ESR groups must only be stereo centers with known configuration.";
    public static final String VALIDATION_ERROR_OVER_UNDER_SPECIFIED = "Over- or under-specified stereo feature or more than one racemic type bond";
    public static final String VALIDATION_ERROR_AMBIGUOUS_CONFIGURATION = "Ambiguous configuration at stereo center because of 2 parallel bonds";
    public static final String[] VALIDATION_ERRORS_STEREO = {
        VALIDATION_ERROR_ESR_CENTER_UNKNOWN,
        VALIDATION_ERROR_OVER_UNDER_SPECIFIED,
        VALIDATION_ERROR_AMBIGUOUS_CONFIGURATION
        };
    
    transient private Canonizer mCanonizer;
    transient private boolean mAssignParitiesToNitrogen;

	public StereoMolecule() {
		}


	public StereoMolecule(int maxAtoms, int maxBonds) {
		super(maxAtoms, maxBonds);
		}


    public StereoMolecule(Molecule mol) {
        super(mol);
        }


    /*
     * Constructor removed to avoid dependancy to FFMolecule
     * You must user the FFMolecule.toStereoMolecule instead
	public StereoMolecule(FFMolecule mol) {
		super(mol);
		}
	*/

	public StereoMolecule createMolecule(int atoms, int bonds) {
		return new StereoMolecule(atoms, bonds);
		}


	public StereoMolecule getCompactCopy() {
		StereoMolecule theCopy = new StereoMolecule(mAllAtoms, mAllBonds);
		copyMolecule(theCopy);
		return theCopy;
		}

	/**
	 * Separates all disconnected fragments of this Molecule into individual Molecule objects.
	 * If fragment separation is only needed, if there are multiple fragments, it may me more
	 * efficient to run this functionality in two steps, e.g.:<br>
	 * int[] fragmentNo = new int[mol.getAllAtoms()];<br>
	 * int fragmentCount = getFragmentNumbers(fragmentNo, boolean);<br>
	 * if (fragmentCount > 1) {<br>
	 *     StereoMolecule[] fragment = getFragments(int[] fragmentNo, fragmentCount);<br>
	 *     ...<br>
	 *     }<br>
	 * @return
	 */
	public StereoMolecule[] getFragments() {
        int[] fragmentNo = new int[mAllAtoms];
        int fragments = getFragmentNumbers(fragmentNo, false);
        return getFragments(fragmentNo, fragments);
        }

	/**
	 * Separates all disconnected fragments of this Molecule into individual molecule objects.
	 * The parameters fragmentNo and fragmentCount are typically obtained from a call of
	 * getFragmentNumbers().
	 * @param fragmentNo
	 * @param fragmentCount
	 * @return
	 */
    public StereoMolecule[] getFragments(int[] fragmentNo, int fragmentCount) {
        StereoMolecule[] fragment = new StereoMolecule[fragmentCount];
        int[] atoms = new int[fragmentCount];
        int[] bonds = new int[fragmentCount];
        int[] atomMap = new int[mAllAtoms];
        for (int atom=0; atom<mAllAtoms; atom++)
        	if (fragmentNo[atom] != -1)
        		atomMap[atom] = atoms[fragmentNo[atom]]++;
        for (int bond=0; bond<mAllBonds; bond++) {
        	int f1 = fragmentNo[mBondAtom[0][bond]];
        	int f2 = fragmentNo[mBondAtom[1][bond]];
        	if (f1 == f2 && f1 != -1)
        		bonds[f1]++;
        	}
        for (int i=0; i<fragmentCount; i++) {
            fragment[i] = createMolecule(atoms[i], bonds[i]);
            copyMoleculeProperties(fragment[i]);
            }
        for (int atom=0; atom<mAllAtoms; atom++)
        	if (fragmentNo[atom] != -1)
        		copyAtom(fragment[fragmentNo[atom]], atom, 0, 0);
        for (int bond=0; bond<mAllBonds; bond++) {
        	int f1 = fragmentNo[mBondAtom[0][bond]];
        	int f2 = fragmentNo[mBondAtom[1][bond]];
        	if (f1 == f2 && f1 != -1)
	            copyBond(fragment[f1], bond, 0, 0, atomMap, false);
            }
        for (StereoMolecule f:fragment) {
        	f.renumberESRGroups(cESRTypeAnd);
        	f.renumberESRGroups(cESRTypeOr);
        	}

        return fragment;
        }

    /**
     * Call ensureHelperArrays(cHelperParities) only if either
     * parities are available anyway (e.g. from idcode parsing)
     * or if coordinates with stereo bonds are available.
     * Call ensureHelperArrays(cHelperCIP) only if coordinates
     * with stereo bonds are available.
     */
	public void ensureHelperArrays(int required) {
        super.ensureHelperArrays(required);

        if ((required & ~mValidHelperArrays) == 0)
			return;

        if (mAssignParitiesToNitrogen)
        	required |= cHelperBitIncludeNitrogenParities;

		for (int atom=0; atom<getAllAtoms(); atom++)
			mAtomFlags[atom] &= ~cAtomFlagsHelper3;
		for (int bond=0; bond<getBonds(); bond++)
			mBondFlags[bond] &= ~cBondFlagsHelper3;

		int rankBits = 0;
		int rankMode = 0;
		if ((required & cHelperBitSymmetrySimple) != 0) {
			rankBits = cHelperBitSymmetrySimple;
		    rankMode = Canonizer.CREATE_SYMMETRY_RANK;
		    }
		else if ((required & cHelperBitSymmetryDiastereotopic) != 0) {
			rankBits = cHelperBitSymmetryDiastereotopic;
            rankMode = Canonizer.CREATE_SYMMETRY_RANK | Canonizer.CONSIDER_DIASTEREOTOPICITY;
		    }
		else if ((required & cHelperBitSymmetryEnantiotopic) != 0) {
			rankBits = cHelperBitSymmetryEnantiotopic;
            rankMode = Canonizer.CREATE_SYMMETRY_RANK | Canonizer.CONSIDER_ENANTIOTOPICITY;
		    }

		if ((required & cHelperBitIncludeNitrogenParities) != 0) {
		    rankBits |= cHelperBitIncludeNitrogenParities;
            rankMode |= Canonizer.ASSIGN_PARITIES_TO_TETRAHEDRAL_N;
		    }

		mCanonizer = new Canonizer(this, rankMode);
		mCanonizer.setParities();
        mCanonizer.setStereoCenters();
		mCanonizer.setCIPParities();

        if (validateESR())  // freshly calculate chirality after racemisation
            mCanonizer = new Canonizer(this, rankMode);

		mValidHelperArrays |= (cHelperBitParities | cHelperBitCIP | rankBits);
		}

    private boolean validateESR() {
        boolean paritiesUpdated = false;

        for (int atom=0; atom<getAtoms(); atom++)
            if (!isAtomStereoCenter(atom)
             || getAtomParity(atom) == cAtomParityUnknown)
                mAtomFlags[atom] &= ~cAtomFlagsESR;

        if (mIsRacemate) {
                // mIsRacemate is set if molecule was decoded from source that
                // contains a non-set chiral flag to indicate that the molecule
                // is actually a racemate of the drawn structure

            if ((mChirality & ~cChiralityIsomerCountMask) != cChiralityMeso) {
                // centers that are unknown or already racemic need to
                // be put into independent groups of type "AND" atoms
                boolean[] isIndependentRacemicAtom = new boolean[getAtoms()];
                for (int atom=0; atom<getAtoms(); atom++)
                    if (isAtomStereoCenter(atom)
                     && getAtomParity(atom) != cAtomParityUnknown
                     && getAtomESRType(atom) == cESRTypeAnd)
                        isIndependentRacemicAtom[atom] = true;
    
                for (int atom=0; atom<getAtoms(); atom++) {
                    if (isAtomStereoCenter(atom)
                     && getAtomParity(atom) != cAtomParityUnknown) {
                        setAtomESR(atom, cESRTypeAnd, 0);
                        paritiesUpdated = true;
                        }
                    }
    
                for (int atom=0; atom<getAtoms(); atom++) {
                    if (isIndependentRacemicAtom[atom]) {
                        setAtomParity(atom, cAtomParity1, false);
                        setAtomESR(atom, cESRTypeAnd, -1);
                        paritiesUpdated = true;
                        }
                    }
                }

            mIsRacemate = false;
            }

        renumberESRGroups(cESRTypeAnd);
        renumberESRGroups(cESRTypeOr);

        return paritiesUpdated;
        }

    /**
     * This returns the absolute(!) atom parity from the canonization procedure.
     * While the molecule's (relative) atom parity returned by getAtomParity() is
     * based on atom indices and therefore depends on the order of atoms,
     * the absolute atom parity is based on atom ranks and therefore independent
     * of the molecule's atom order.
     * Usually relative parities are used, because the atom's stereo situation
     * can be interpreted without the need for atom rank calculation.
     * This requires valid helper arrays level cHelperParities or higher.
     * @param atom
     * @return one of the Molecule.cAtomParityXXX constants
     */
    public int getAbsoluteAtomParity(int atom) {
        return mCanonizer.getTHParity(atom);
        }

    /**
     * This returns the absolute(!) bond parity from the canonization procedure.
     * While the molecule's (relative) bond parity returned by getBondParity() is
     * based on atom indices and therefore depends on the order of atoms,
     * the absolute bond parity is based on atom ranks and therefore independent
     * of the molecule's atom order.
     * Usually relative parities are used, because the bond's stereo situation
     * can be interpreted without the need for atom rank calculation.
     * This requires valid helper arrays level cHelperParities or higher.
     * @param bond
     * @return one of the Molecule.cBondParityXXX constants
     */
    public int getAbsoluteBondParity(int bond) {
        return mCanonizer.getEZParity(bond);
        }

    /**
     * This returns atom symmetry numbers from within the molecule
     * canonicalization procedure. Atoms with same symmetry numbers
     * can be considered topologically equivalent. Symmetry ranks are
     * only available after calling ensureHelperArrays(cHelperSymmetry...).
     * In mode cHelperSymmetrySimple stereoheterotopic atoms are considered
     * equivalent. In mode cHelperSymmetryDiastereotopic only diastereotopic
     * atoms are distinguished. In mode cHelperSymmetryEnantiotopic all
     * stereoheterotopic atoms, i.e. enantiotopic and diastereotopic atoms,
     * are distinguished.
     */
    public int getSymmetryRank(int atom) {
        return mCanonizer.getSymmetryRank(atom);
        }

    /**
     * This is a convenience method that creates the molecule's idcode
     * without explicitly creating a Canonizer object for this purpose.
     * The idcode is a compact String that uniquely encodes the molecule
     * with all stereo and query features.
     * @return
     */
    public String getIDCode() {
        ensureHelperArrays(cHelperParities);
        return mCanonizer.getIDCode();
        }

    /**
     * This is a convenience method that creates the molecule's id-coordinates
     * matching the idcode available with getIDCode().
     * It does not explicitly create a Canonizer object for this purpose.
     * @return
     */
    public String getIDCoordinates() {
        ensureHelperArrays(cHelperParities);
        return mCanonizer.getEncodedCoordinates();
        }

    /**
     * This is a convenience method returning the StereoMolecule's Canonizer
     * object after calling internally ensureHelperArrays(cHelperParities) and,
     * thus, effectively running the canonicalization and validating the Canonizer itself.
     * @return
     */
    public Canonizer getCanonizer() {
        ensureHelperArrays(cHelperParities);
        return mCanonizer;
        }

    public int getStereoCenterCount() {
        ensureHelperArrays(cHelperCIP);
        int scCount = 0;
        for (int atom=0; atom<getAtoms(); atom++)
            if (getAtomParity(atom) != cAtomParityNone
             && !isAtomParityPseudo(atom))
                scCount++;
        return scCount;
        }


	/**
	 * Sets all atoms with TH-parity 'unknown' to explicitly defined 'unknown'.
	 * Sets all double bonds with EZ-parity 'unknown' to cross bonds.
	 */
	public void setUnknownParitiesToExplicitlyUnknown() {
		ensureHelperArrays(cHelperCIP);
		mCanonizer.setUnknownParitiesToExplicitlyUnknown();
		}


    /**
     * This is a policy setting for this StereoMolecule as molecule container.
     * If set to true then this StereoMolecule will treat tetrahedral nitrogen atoms
     * with three or four distinguishable substituents as stereo centers and will
     * assign parities. deleteMolecule() does not change this behavior.
     * @param b
     */
    public void setAssignParitiesToNitrogen(boolean b) {
    	mAssignParitiesToNitrogen = b;
    	mValidHelperArrays &= ~(cHelperParities | cHelperBitIncludeNitrogenParities);
    	}


    public void validate() throws Exception {
		super.validate();

		ensureHelperArrays(cHelperCIP);
		for (int atom=0; atom<getAtoms(); atom++) {
                // This also causes the cAtomFlagStereoProblem flag to be set;
                // therefore handle it first to distinguish it from the next.
            if ((getAtomESRType(atom) == cESRTypeAnd
              || getAtomESRType(atom) == cESRTypeOr)
             && (!isAtomStereoCenter(atom)
              || getAtomParity(atom) == cAtomParityUnknown))
                throw new Exception("Members of ESR groups must only be stereo centers with known configuration.");

            if ((mAtomFlags[atom] & cAtomFlagStereoProblem) != 0)
				throw new Exception("Over- or under-specified stereofeature or more than one racemic type bond");

			if ((getAtomParity(atom) == Molecule.cAtomParity1
			  || getAtomParity(atom) == Molecule.cAtomParity2)
			 && getAtomPi(atom) == 0) {
				float[] angle = new float[getConnAtoms(atom)];
				for (int i=0; i<getConnAtoms(atom); i++)
					angle[i] = getBondAngle(atom, getConnAtom(atom, i));
				for (int i=1; i<getConnAtoms(atom); i++)
					if (!isStereoBond(getConnBond(atom, i), atom))
						for (int j=0; j<i; j++)
							if (!isStereoBond(getConnBond(atom, j), atom))
								if (bondsAreParallel(angle[i], angle[j]))
									throw new Exception("Ambiguous configuration at stereo center because of 2 parallel bonds");
				}
			}
		}

	public String getChiralText() {
		ensureHelperArrays(cHelperCIP);

        int count = mChirality & cChiralityIsomerCountMask;

        switch (mChirality & ~cChiralityIsomerCountMask) {
        case cChiralityNotChiral:
			return null;
        case cChiralityMeso:
            return (count == 1) ? "meso" : ""+count+" meso diastereomers";
        case cChiralityUnknown:
			return "unknown chirality";
        case cChiralityRacemic:
            return "racemate";
        case cChiralityKnownEnantiomer:
            return "this enantiomer";
        case cChiralityUnknownEnantiomer:
            return "this or other enantiomer";
        case cChiralityEpimers:
            return "two epimers";
        default:
            return (count == 1) ? "one stereo isomer" : ""+count+" stereo isomers";
            }
		}

	private void writeObject(ObjectOutputStream stream) throws IOException {}
	private void readObject(ObjectInputStream stream) throws IOException {}
	}
