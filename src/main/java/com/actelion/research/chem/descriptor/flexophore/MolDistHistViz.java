/*
 * Copyright (c) 2020.
 * Idorsia Pharmaceuticals Ltd., Hegenheimermattweg 91, CH-4123 Allschwil, Switzerland
 *
 *  This file is part of DataWarrior.
 *
 *  DataWarrior is free software: you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation, either version 3 of
 *  the License, or (at your option) any later version.
 *
 *  DataWarrior is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU General Public License for more details.
 *  You should have received a copy of the GNU General Public License along with DataWarrior.
 *  If not, see http://www.gnu.org/licenses/.
 *
 *  @author Modest v. Korff
 *
 */

package com.actelion.research.chem.descriptor.flexophore;

import com.actelion.research.calc.ArrayUtilsCalc;
import com.actelion.research.chem.*;
import com.actelion.research.chem.descriptor.flexophore.calculator.StructureCalculator;
import com.actelion.research.chem.descriptor.flexophore.generator.ConstantsFlexophoreGenerator;
import com.actelion.research.chem.interactionstatistics.InteractionAtomTypeCalculator;

import com.actelion.research.chem.phesa.pharmacophore.IPharmacophorePoint;
import com.actelion.research.chem.phesa.pharmacophore.PharmacophoreCalculator;
import com.actelion.research.util.graph.complete.ICompleteGraph;

import java.io.Serializable;
import java.util.*;

/**
 * Class for Flexophore visualization and atom tracking. Information about corresponding atoms is stored in PPNodeViz.
 */
public class MolDistHistViz extends DistHist implements Serializable, IMolDistHist, ICompleteGraph {
	
	private static final long serialVersionUID = 15052013;

	public static final int DESCRIBE_ALL = 1;
	/**
	 * Only mapped atoms are described.
	 */
	public static final int DESCRIBE_MAPPED = 2;

	public static final int CAPACITY_INEVITABLE_PPPOINTS = 5;

	public static final String TAG_VIZ_INFO_ENCODED = "Flexophore2VizInfo";
	
	private static final int MIN_COUNTS_BLURR = 3;
	private static final double RATIO_BLURR = 1.0/5.0;
	
	private static transient MDHIndexTables indexTables;
	
	/**
	 * Colors for visualization of the Flexophore mapping.
	 */
	public static transient final String [] COLORS = {
		"aquamarine", 
		"blue", 
		"violet", 
		"cyan", 
		"green", 
		"lavender",  
		"lime", 
		"limegreen",  
		"linen", 
		"magenta", 
		"maroon", 
		"olive", 
		"purple", 
		"red", 
		"tan", 
		"turquoise",  
		"yellow"}; 
	
	private List<PPNodeViz> liPPNodeViz;
	
	protected Molecule3D molecule3D;
	
	private int flagsDescribe; 
	
	// Exclusive C nodes
	private int numCNodes;

	private int numHeteroNodes;
	
	private boolean finalized;

	private HashSet<Integer> hsIndexInevitablePPPoints;
	
	// List of the original distance table generated by the conformations.
	private List<float[][]> liDistanceTable;


	private byte modeFlexophore;

	public MolDistHistViz() {
		super();
		
		molecule3D = null;
		
		flagsDescribe = DESCRIBE_ALL;
		
		liPPNodeViz = new ArrayList<>();
		
		hsIndexInevitablePPPoints = new HashSet<>();

		modeFlexophore = ConstantsFlexophore.MODE_SOFT_PPPOINTS;
		
	}
	
	public MolDistHistViz(int nNodes) {
		initHistogramArray(nNodes);
		
		flagsDescribe = DESCRIBE_ALL;
		
		hsIndexInevitablePPPoints = new HashSet<>();

		modeFlexophore = ConstantsFlexophore.MODE_SOFT_PPPOINTS;
	}
	
	public MolDistHistViz(int nNodes, Molecule3D molecule3D) {
		initHistogramArray(nNodes);
		
		flagsDescribe = DESCRIBE_ALL;
		
		if(molecule3D!=null) {
			this.molecule3D = new Molecule3D(molecule3D);
			this.molecule3D.ensureHelperArrays(Molecule.cHelperRings);
		}
		
		hsIndexInevitablePPPoints = new HashSet<>();

		modeFlexophore = ConstantsFlexophore.MODE_SOFT_PPPOINTS;
	}
	
	public MolDistHistViz(MolDistHistViz mdhv) {
		
		hsIndexInevitablePPPoints = new HashSet<Integer>();
		
		mdhv.copy(this);
		
		flagsDescribe = DESCRIBE_ALL;

		modeFlexophore = mdhv.modeFlexophore;

	}
	
	public MolDistHistViz(MolDistHist mdh) {
		if(mdh.getNumPPNodes()==0){
			throw new RuntimeException("Empty object given into constructor.");
		}
		
		mdh.copy(this);

		modeFlexophore = mdh.getModeFlexophore();
		
		liPPNodeViz=new ArrayList<PPNodeViz>(mdh.getNumPPNodes());
		for (int i = 0; i < mdh.getNumPPNodes(); i++) {
			PPNodeViz node = new PPNodeViz(mdh.getNode(i));
			liPPNodeViz.add(node);
		}
		
		hsIndexInevitablePPPoints = new HashSet<Integer>();
		
		realize();
	}

	public static void createIndexTables(){
		indexTables = MDHIndexTables.getInstance();
	}
	
	public void addInevitablePharmacophorePoint(int indexPPNode){
		hsIndexInevitablePPPoints.add(indexPPNode);
	}

	public void removeInevitablePharmacophorePoint(int indexPPNode){
		hsIndexInevitablePPPoints.remove(indexPPNode);
	}

	public void setModeFlexophore(byte modeFlexophore) {
		this.modeFlexophore = modeFlexophore;
		for (PPNodeViz n : liPPNodeViz) {
			n.setModeFlexophore(modeFlexophore);
		}
	}

	public void setMarkAll(boolean mark){
		for (PPNodeViz n : liPPNodeViz) {
			n.setMarked(mark);
		}
	}

	public void setMark(int index, boolean mark){
		liPPNodeViz.get(index).setMarked(mark);
	}
	
	public boolean isMarked(int index){
		return liPPNodeViz.get(index).isMarked();
	}
	
	/**
	 * 
	 * @param node
	 * @return index of the node.
	 */
	public int addNode(PPNodeViz node) {
		
		int index = liPPNodeViz.size();
		
		node.setIndex(liPPNodeViz.size());

		node.setModeFlexophore(modeFlexophore);

		liPPNodeViz.add(node);

		if(liPPNodeViz.size()>getNumPPNodes()){
			throw new RuntimeException("To many nodes added!");
		}

		finalized = false;
		
		return index;
	}

	public boolean check(){
		boolean bOK = true;
		
		int nodes = getNumPPNodes();
		for (int i = 0; i < nodes; i++) {
			PPNode node = getNode(i);
			int ats = node.getInteractionTypeCount();
			for (int j = 0; j < ats; j++) {
				int inttype = node.getInteractionType(j);
				String s = InteractionAtomTypeCalculator.getString(inttype);
				if(s.length()==0) {
					bOK = false;
				}
			}
		}
		return bOK;
	}

	
	protected void initHistogramArray(int size) {
		super.initHistogramArray(size);
		
		liPPNodeViz = new ArrayList<>();
		
		finalized = false;
		
	}

	public MolDistHistViz copy(){
		return new MolDistHistViz(this);
	}
	
	/**
	 * 
	 * @param copy This is written into copy.
	 */
	public void copy(MolDistHistViz copy){
		
		super.copy(copy);
		
		copy.flagsDescribe = flagsDescribe;
		
		if(molecule3D !=null)
			copy.molecule3D = new Molecule3D(molecule3D);
		
		copy.liPPNodeViz = new ArrayList<PPNodeViz>();

		for (int i = 0; i < liPPNodeViz.size(); i++) {
			copy.liPPNodeViz.add(new PPNodeViz(liPPNodeViz.get(i)));
		}
		
		// Exclusive C nodes
		copy.numCNodes=numCNodes;

		copy.numHeteroNodes=numHeteroNodes;
		
		copy.finalized=finalized;
		
		copy.hsIndexInevitablePPPoints.clear();
		
		copy.hsIndexInevitablePPPoints.addAll(hsIndexInevitablePPPoints);
		
	}
	
	/**
	 * Recalculates the coordinates off the pharmacophore nodes. 
	 * Has to be called after changing the coordinates for the Molecule3D.
	 */
	public void recalculateCoordPPPoints(){
			
		for (PPNodeViz ppNodeViz : liPPNodeViz) {
			
			List<Integer> liIndexAts = ppNodeViz.getListIndexOriginalAtoms();
			
			int [] arrAtIndex = new int [liIndexAts.size()];
			
			for (int i = 0; i < arrAtIndex.length; i++) {
				arrAtIndex[i]=liIndexAts.get(i);
			}
			
			Coordinates [] arrCoordinates = new Coordinates [arrAtIndex.length];
			for (int i = 0; i < arrAtIndex.length; i++) {
				
				double x = molecule3D.getAtomX(arrAtIndex[i]);
				double y = molecule3D.getAtomY(arrAtIndex[i]);
				double z = molecule3D.getAtomZ(arrAtIndex[i]);
				arrCoordinates[i] = new Coordinates(x, y, z);
				
			}

			Coordinates coordCenter = Coordinates.createBarycenter(arrCoordinates);
			ppNodeViz.setCoordinates(coordCenter.x, coordCenter.y, coordCenter.z);	
		}

	}
	
	
	public void resetInevitablePharmacophorePoints(){
		hsIndexInevitablePPPoints.clear();
	}
	
	public void resetInfoColor(){
		
		int size =	getNumPPNodes();
		
		for (int i = 0; i < size; i++) {
			
			PPNodeViz node = getNode(i);
			
			node.resetInfoColor();
		}
	}

	/**
	 * This index is used to track the fate of the nodes
	 * MvK 17.07.2007
	 *
	 */
	public void createNodeIndex(){
		for (int i = 0; i < getNumPPNodes(); i++) {
			getNode(i).setIndex(i);
		}
	}
	
	
	/**
	 * 
	 * @param index 0 or 1
	 * @param bond index of the bond array
	 * @return the atom index
	 */
	public int getBondAtom(int index, int bond) {
		return indexTables.getAtomPairsBondsTable(getNumPPNodes())[index][bond];
		// return mArrAtPairsBonds[index][bond];
	}
	
	public int getConnAtom(int at, int index) {
		if(index >= at)
			index++;
		
		return index;
		
	}
	
	public int getConnBond(int at, int index) {
		return indexTables.getConnectionTable(getNumPPNodes())[at][index];
		
	}
	
	public int getIndexFromCoord(double x, double y, double z) {
		int index = -1;
		
		Coordinates c = new Coordinates(x, y, z);
		
		for (int i = 0; i < getNumPPNodes(); i++) {
			PPNodeViz ppNodeViz = getNode(i);
			
			if(ppNodeViz.getCoordinates().equals(c)){
				index = i;
				break;
			}
		}
		
		return index;
	}
	
	public int getInfo(int index) {
		return ((PPNodeViz)getNode(index)).getMappingIndex();
	}
	
	public PPNodeViz getNode(int i){
		return liPPNodeViz.get(i);
	}
	
	/**
	 * 
	 * @return shallow copy.
	 */
	public List<PPNodeViz> getNodes(){
		return liPPNodeViz;
	}
	

	public void set(List<PPNodeViz> liPPNodeViz) {
		this.liPPNodeViz = liPPNodeViz;
		calculate();
	}
	
	/**
	 * 
	 * @param ff has to be the molecule the descriptor was derived from.
	 */
	public void set(Molecule3D ff ){
		if(ff!=null)
			molecule3D = new Molecule3D(ff);
	}
	
	public void setMappingIndex(int index, int info) {
		getNode(index).setMappingIndex(info);
	}
	
	public void setSimilarityMappingNodes(int index, float similarityMappingNodes) {
		getNode(index).setSimilarityMappingNodes(similarityMappingNodes);
	}
	
	
	public String getName() {
			return molecule3D.getName();
	}
	
	public void setName(String name) {
		molecule3D.setName(name);
	}
	
	public boolean isOnlyCarbon(int index){
		PPNode node = getNode(index);
		boolean bOnlyCarbon = true;
		for (int i = 0; i < node.getInteractionTypeCount(); i++) {
			if(node.getAtomicNo(i) != 6)
				bOnlyCarbon = false;
		}
		return bOnlyCarbon;
	}
	
	private int calcNumHeteroNodes(){
		int num=0;
		for (int i = 0; i < getNumPPNodes(); i++) {
			PPNode node = getNode(i);
			if(node.hasHeteroAtom())
				num++;
			
		}
		return num;
	}
	
	public void canonize(){
		
		for (int i = 0; i < liPPNodeViz.size(); i++) {
			liPPNodeViz.get(i).realize();
			liPPNodeViz.get(i).sortInteractionTypes();
		}
		
		boolean fin=false;
		while(!fin){
			fin=true;
			for (int i = 1; i < liPPNodeViz.size(); i++) {
				int cmp = compareNodes(i, i-1);
				
				if(cmp<0){
					fin=false;
					swapNodes(i, i-1);
				}
			}
		}
		
		for (int i = 0; i < liPPNodeViz.size(); i++) {
			liPPNodeViz.get(i).setIndex(i);
		}

	}
	
	private int compareNodes(int n1, int n2){
		int cmp=0;
		
		// cmp = PPNode.compare(mArrNode[n1], mArrNode[n2]);
		
		PPNode pp1 = liPPNodeViz.get(n1);
		PPNode pp2 = liPPNodeViz.get(n2);
		cmp = pp1.compareTo(pp2);
		
		if(cmp==0){
			// Here we compare the histograms
			int size = getNumPPNodes()-1;
			List<byte[]> liN1 = new ArrayList<byte[]>(size);
			List<byte[]> liN2 = new ArrayList<byte[]>(size);
			for (int i = 0; i < liPPNodeViz.size(); i++) {
				if(i!=n1){
					liN1.add(getDistHist(n1, i));
				} 
				
				if(i!=n2){
					liN2.add(getDistHist(n2, i));
				} 
			}
			
			class CmpHists implements Comparator<byte[]> {
				public int compare(byte [] arr1, byte [] arr2) {
					int cmp = 0;
					
					for (int i = 0; i < arr1.length; i++) {
						if(arr1[i]>arr2[i]) {
							cmp = 1;
							break;
						} else if(arr1[i]<arr2[i]) {
							cmp = -1;
							break;
						}
					}
					return cmp;
				}
			}
			
			Collections.sort(liN1, new CmpHists());
			Collections.sort(liN2, new CmpHists());
			
			for (int i = 0; i < liN1.size(); i++) {
				int cmpHist = compare(liN1.get(i), liN2.get(i));
				if(cmpHist!=0){
					cmp = cmpHist;
					break;
				}
			}
		}
		return cmp;
	}
	
	public void swapNodes(int n1, int n2){
		
		PPNodeViz p1 = liPPNodeViz.get(n1);
		
		int size = getNumPPNodes();
		liPPNodeViz.set(n1, liPPNodeViz.get(n2));
		liPPNodeViz.set(n2, p1);
		
		for (int i = 0; i < size; i++) {
			if((i!=n1) && (i!=n2)){
				byte [] histTmp = getDistHist(n1,i);
				setDistHist(n1,i, getDistHist(n2,i));
				setDistHist(n2,i, histTmp);
			}
		}
	}
	
	private int compare(byte [] arr1, byte [] arr2) {
		int cmp = 0;
		
		for (int i = 0; i < arr1.length; i++) {
			if(arr1[i]>arr2[i]) {
				cmp = 1;
				break;
			} else if(arr1[i]<arr2[i]) {
				cmp = -1;
				break;
			}
		}
		return cmp;
	}

	public int getNumCExclusiveNodes(){
		return numCNodes;
	}

	public int getNumHeteroNodes(){
		return numHeteroNodes;
	}
	
	public List<Integer> getInevitablePharmacophorePoints(){
		
		List<Integer> li = new ArrayList<Integer>(hsIndexInevitablePPPoints);
		
		return li;
	}

	
	protected HashSet<Integer> getHashSetIndexInevitablePPPoints() {
		return hsIndexInevitablePPPoints;
	}

	public int getNumInevitablePharmacophorePoints(){
		return hsIndexInevitablePPPoints.size();
	}
	
	public boolean isInevitablePharmacophorePoint(int indexNode){
		
		return hsIndexInevitablePPPoints.contains(indexNode);
	}
	
	public boolean isAliphatic(int indexNode) {
		
		boolean aliphatic = true;

		PPNodeViz node = getNode(indexNode);
		if(modeFlexophore==ConstantsFlexophore.MODE_HARD_PPPOINTS){
			if(PharmacophoreCalculator.LIPO_ID == node.get()[0]){
				aliphatic=true;
			}
		} else {
			for (int i = 0; i < node.getInteractionTypeCount(); i++) {

				if (node.getAtomicNo(i) != 6) {
					aliphatic = false;
					break;
				}
			}
		}

		return aliphatic;
	}

	public boolean isAcceptor(int indexNode) {
		boolean acceptor = false;
		
		PPNodeViz node = getNode(indexNode);
		if(modeFlexophore==ConstantsFlexophore.MODE_HARD_PPPOINTS){
			if(IPharmacophorePoint.Functionality.ACCEPTOR.getIndex()==node.get()[0]){
				acceptor=true;
			}

		} else {
			for (int i = 0; i < node.getInteractionTypeCount(); i++) {

				if (node.getAtomicNo(i) == 8 || node.getAtomicNo(i) == 7) {
					acceptor = true;
					break;
				}
			}
		}
		
		return acceptor;
	}
	
	public boolean isDonor(int indexNode) {
		
		boolean donor = false;

		PPNodeViz node = getNode(indexNode);

		if(modeFlexophore==ConstantsFlexophore.MODE_HARD_PPPOINTS){

			if(IPharmacophorePoint.Functionality.DONOR.getIndex()==node.get()[0]){
				donor=true;
			}

		} else {
			List<Integer> liIndexAtom = node.getListIndexOriginalAtoms();

			StereoMolecule mol = new Molecule3D(molecule3D);

			mol.ensureHelperArrays(Molecule.cHelperRings);

			for (int indexAtom : liIndexAtom) {

				if (mol.getAtomicNo(indexAtom) == 8 || mol.getAtomicNo(indexAtom) == 7) {

					if (mol.getAllHydrogens(indexAtom) > 0) {
						donor = true;
						break;
					}
				}
			}
		}

		return donor;
	}

	public boolean isAromatic(int indexNode) {

		boolean aromatic = false;

		PPNodeViz node = getNode(indexNode);

		if(modeFlexophore==ConstantsFlexophore.MODE_HARD_PPPOINTS){
			if(IPharmacophorePoint.Functionality.AROM_RING.getIndex()==node.get()[0]){
				aromatic=true;
			}
		}

		return aromatic;
	}

	public boolean isChargePos(int indexNode) {

		boolean charge = false;

		PPNodeViz node = getNode(indexNode);

		if(modeFlexophore==ConstantsFlexophore.MODE_HARD_PPPOINTS){
			if(IPharmacophorePoint.Functionality.POS_CHARGE.getIndex()==node.get()[0]){
				charge=true;
			}
		}

		return charge;
	}

	public boolean isChargeNeg(int indexNode) {

		boolean charge = false;

		PPNodeViz node = getNode(indexNode);

		if(modeFlexophore==ConstantsFlexophore.MODE_HARD_PPPOINTS){
			if(IPharmacophorePoint.Functionality.NEG_CHARGE.getIndex()==node.get()[0]){
				charge=true;
			}
		}

		return charge;
	}


	private int calcNumCExclusiveNodes(){
		int num=0;
		for (int i = 0; i < getNumPPNodes(); i++) {
			PPNodeViz node = getNode(i);
			if(node.isCarbonExclusiveNode())
				num++;
			
		}
		return num;
	}
	
	public void realize() {
		// super.realize();
		
		for(PPNodeViz node : liPPNodeViz){
			node.realize();
		}
		
		canonize();
		
		calculate();
		
		finalized=true;
		
	}
	
	public void blurrSingleBinHistograms(){
		
		int size = getNumPPNodes();
		
		byte [] arr = new byte [ConstantsFlexophoreGenerator.BINS_HISTOGRAM];
		
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				if(i==j)
					continue;
				
				arr = getDistHist(i, j, arr);
				
				int occupied=0;
				int counts=0;
				for (int k = 0; k < arr.length; k++) {
					if(arr[k]>0){
						occupied++;
						counts+=arr[k];
					}
				}
				
				if(occupied==1){
					if( counts >= MIN_COUNTS_BLURR){
						blurrSingleBinHistogram(arr);
						
						setDistHist(i, j, arr);
					}
				}
				
				
			}
		}
		
		
	}
	
	private void blurrSingleBinHistogram(byte [] arr){
		int pos = -1;
		for (int i = 0; i < arr.length; i++) {
			if(arr[i]>0){
				pos = i;
				break;
			}
		}
		
		int countCenter = arr[pos];
		
		if((pos==0) || (pos == arr.length-1)){
			
			byte countCenterBlurred = (byte)(countCenter - (countCenter*RATIO_BLURR)); 
			byte countBlurredBuddy = (byte)(countCenter*RATIO_BLURR); 
					
			if(pos==0) {
				arr[0]=countCenterBlurred;
				arr[1]=countBlurredBuddy;
			}else if(pos==arr.length-1) {
				arr[arr.length-1]=countCenterBlurred;
				arr[arr.length-2]=countBlurredBuddy;
			}
			
		} else {
			
			byte countCenterBlurred = (byte)(countCenter - (2.0 * countCenter*RATIO_BLURR)); 
			byte countBlurredBuddy = (byte)(countCenter*RATIO_BLURR); 
			arr[pos-1]=countBlurredBuddy;
			arr[pos]=countCenterBlurred;
			arr[pos+1]=countBlurredBuddy;
		}
		
	}

	public void calculate() {
		numCNodes = calcNumCExclusiveNodes();
		numHeteroNodes = calcNumHeteroNodes();
	}

	/**
	 * Remove all atoms without connections.
	 * @param mol
	 * @return
	 */
	protected static Molecule3D finalizeMolecule(Molecule3D mol) {
		Molecule3D molecule3DCpy = new Molecule3D(mol);
		molecule3DCpy.ensureHelperArrays(Molecule.cHelperRings);
		
		HashSet<Integer> hsAt2Del = new HashSet<Integer>();
		for (int i = 0; i < molecule3DCpy.getAllAtoms(); i++) {
			if(molecule3DCpy.getConnAtoms(i)==0)
				hsAt2Del.add(i);
		}
		
		List<Integer> liAt2Del = new ArrayList<Integer>(hsAt2Del);
		Collections.sort(liAt2Del);
		Collections.reverse(liAt2Del);
		
		for (Integer at : liAt2Del) {
			molecule3DCpy.deleteAtom(at);
		}
		
		return molecule3DCpy;
	}
	
	/**
	 * 
	 * @return deep object.
	 */
	public MolDistHist getMolDistHist(){

		realize();

		int nPPNodes = getNumPPNodes();

		MolDistHist mdh = new MolDistHist(nPPNodes);

		for (int i = 0; i < nPPNodes; i++) {
			mdh.addNode(getNode(i));
		}
		
		for (int i = 0; i < nPPNodes ; i++) {
			for (int j = i+1; j < nPPNodes ; j++) {
				mdh.setDistHist(i, j, getDistHist(i,j));
			}
		}
		
		return mdh;
	}
	
	public double getMaximumDistanceInPPPoint(int indexNode) {
		
		double maxDist = 0;
		
		PPNodeViz node = getNode(indexNode);
			
		List<Integer> liIndexAtom = node.getListIndexOriginalAtoms();
		
		List<Coordinates> liCoord = new ArrayList<Coordinates>();
		for (int atom : liIndexAtom) {
			Coordinates coord = molecule3D.getCoordinates(atom);
			liCoord.add(coord);
		}
		
		
		for (int i = 0; i < liCoord.size(); i++) {
			Coordinates c1 = liCoord.get(i); 
			for (int j = i+1; j < liCoord.size(); j++) {
				Coordinates c2 = liCoord.get(j); 
		
				double dist = c1.distance(c2);
				
				if(dist>maxDist){
					maxDist = dist;
				}
			}
		}
		
		return maxDist;
	}
	
	/**
	 * The atoms of the ff molecule contain the corresponding PPNode indices in the first field of the PPP vector.
	 * @return
	 */
	public Molecule3D getMolecule() {
		
		if(molecule3D == null)
			return null;
		
		return molecule3D;
	}
	
	public Molecule3D getMoleculeRemovedUnrelatedAtoms() {
		
		Molecule3D ff = finalizeMolecule(molecule3D);

		// Adds all atom indices 
		HashSet<Integer> hsIndexUnique = new HashSet<Integer>();
		for(int i=0; i < getNumPPNodes(); i++){
			PPNodeViz node = (PPNodeViz)getNode(i);
			List<Integer> liOriginalIndex = node.getListIndexOriginalAtoms();
			hsIndexUnique.addAll(liOriginalIndex);
			
//			int indAtom = ff.addAtom(26);
//			ff.setCoordinates(indAtom, node.getCoordinates());
//			ff.addBond(indAtom, liOriginalIndex.get(0), Molecule.cBondTypeSingle);
//			hsIndexUnique.add(indAtom);
		}
		
		List<Integer> liInd = new ArrayList<Integer>(hsIndexUnique);
		
		
		HashSet<Integer> hsIndexOnPath = new HashSet<Integer>();
		for (int i = 0; i < liInd.size(); i++) {
			for (int j = i+1; j < liInd.size(); j++) {
				int [] arrIndAtoms = StructureCalculator.getAtomsOnPath(ff, liInd.get(i), liInd.get(j));
				for (int k = 0; k < arrIndAtoms.length; k++) {
					hsIndexOnPath.add(arrIndAtoms[k]);
				}
			}
		}
		
		hsIndexUnique.addAll(hsIndexOnPath);
		
		for (int i = ff.getAllAtoms()-1; i >= 0; i--) {
			if(!hsIndexUnique.contains(i)){
				ff.deleteAtom(i);
			}
		}
				
		return ff;
	}
	
	public int hashCode() {
		String s = toString();
		s = s.replace(" ", "");
		return s.hashCode();
	}
	
	public String toStringInevitable() {
		
		StringBuilder sb = new StringBuilder();
		sb.append("Index inevitable ");
		
		for (int index : hsIndexInevitablePPPoints) {
			sb.append(index + " ");
		}
		sb.append("\n");
		sb.append("Num inevitable " + hsIndexInevitablePPPoints.size());

		return sb.toString();
	}

	public String toString(){
		
		if(!finalized)
			realize();
		
		StringBuffer b = new StringBuffer();
		
		b.append("[");
		for (int i = 0; i < getNumPPNodes(); i++) {
			b.append(getNode(i).toString());
			if(i<getNumPPNodes()-1){
				b.append(" ");
			} else {
				b.append("]");
			}
		}
		
		for (int i = 0; i < getNumPPNodes(); i++) {
			for (int j = i+1; j < getNumPPNodes(); j++) {
				byte [] arrHist = getDistHist(i,j);
				
				if(arrHist!=null)
					b.append("[" + ArrayUtilsCalc.toString(arrHist) + "]");
			}
		}
		
		return b.toString();
	}
	
	public String toStringShort(){
		
		if(!finalized)
			realize();
		
		StringBuffer b = new StringBuffer();
		
		b.append("[");
		for (int i = 0; i < getNumPPNodes(); i++) {
			b.append(getNode(i).toStringShort());
			if(i<getNumPPNodes()-1){
				b.append(" ");
			} else {
				b.append("]");
			}
		}
		
		for (int i = 0; i < getNumPPNodes(); i++) {
			for (int j = i+1; j < getNumPPNodes(); j++) {
				byte [] arrHist = getDistHist(i,j);
				
				if(arrHist!=null)
					b.append("[" + ArrayUtilsCalc.toString(arrHist) + "]");
			}
		}
		
		return b.toString();
	}
	
	
	public boolean equals(Object o) {
		boolean bEQ=true;
		
		MolDistHistViz mdhv=null;
		try {
			mdhv = (MolDistHistViz)o;
		} catch (RuntimeException e) {
			return false;
		}
		
		
		if(getNumPPNodes() != mdhv.getNumPPNodes())
			return false;
		
		
		for (int i = 0; i < getNumPPNodes(); i++) {
			PPNode n1 = getNode(i);
			PPNode n2 = mdhv.getNode(i);
			if(!n1.equals(n2)){
				bEQ = false;
				break;
			}
		}
		
		for (int i = 0; i < getNumPPNodes(); i++) {
			for (int j = i+1; j < getNumPPNodes(); j++) {
				byte [] a1 = getDistHist(i,j);
				byte [] a2 = mdhv.getDistHist(i,j);
				for (int k = 0; k < a2.length; k++) {
					if(a1[k]!=a2[k]){
						bEQ = false;
						break;
					}
				}
			}
		}
		
		return bEQ;
	}
	

	/**
	 * The distance tables which were generated from the conformations.
	 * @param liDistanceTable
	 */
	public void setDistanceTables(List<double[][]> liDistanceTable){
		
		this.liDistanceTable = new ArrayList<float[][]>();
		
		for (double[][] ds : liDistanceTable) {
			float [][] arrDT = new float [ds.length][ds.length];
			
			for (int i = 0; i < ds.length; i++) {
				for (int j = 0; j < ds.length; j++) {
					arrDT[i][j]=(float)ds[i][j];
				}
			}
			
			this.liDistanceTable.add(arrDT);
			
		}
		
	}
	
	public List<float[][]> getDistanceTables() {
		return liDistanceTable;
	}
	

	
	
	protected static String formatDescription(String s){
		
		StringTokenizer st = new StringTokenizer(s, ",");
		
		HashSet<String> set = new HashSet<String>();
		while(st.hasMoreTokens())  {
			String tok = st.nextToken().trim();
			if(!set.contains(tok)){
				set.add(tok);
			}
				
		}
		
		List<String> li = new ArrayList<String>(set);
		
		Collections.sort(li);
		
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < li.size(); i++) {
			if(i>0 && i < li.size()-1){
				sb.append(",");
			}
			sb.append(li.get(i));
		}
		
		return sb.toString();
	}
	
	
	/**
	 * Merges the histograms from mdh into mdhviz.
	 * @param mdhviz has new histograms afterwards.
	 * @param mdh stays unchanged. 
	 * @deprecated
	 */
	public static void merge(MolDistHistViz mdhviz, MolDistHist mdh){
		
		if(mdh.getNumPPNodes()!=mdhviz.getNumPPNodes()) {
			throw new RuntimeException("Size differs.");
		} 
		
		for (int i = 0; i < mdh.getNumPPNodes(); i++) {
			if(!mdh.getNode(i).equalAtoms(mdhviz.getNode(i))){
				throw new RuntimeException("Node " + i + " differs. "+mdh.getNode(i)+"<>"+mdhviz.getNode(i)+" "+mdh.getNode(i).getAtomicNo(i)+" "+mdhviz.getNode(i).getAtomicNo(i));
			}
		}
		
		for (int i = 0; i < mdh.getNumPPNodes(); i++) {
			for (int j = 1+i; j < mdh.getNumPPNodes(); j++) {
				mdhviz.setDistHist(i, j, mdh.getDistHist(i, j));
			}
		}
	}

	/**
	 * Summarizes alkane cluster. The central node may not be a alkane cluster.
	 * The interaction types of the cluster members are added to the interaction 
	 * types of the center node.
	 * @param mdh
	 * @param maxDistance
	 * @return
	 */
	public static MolDistHistViz summarizeAlkaneCluster(MolDistHistViz mdh, int maxDistance) {
		
		List<ClusterNode> liCluster = mdh.getClusterCenter(maxDistance);
		
		List<Integer> liIndexNode = new ArrayList<Integer>();
		for (int i = 0; i < mdh.getNumPPNodes(); i++) {
			liIndexNode.add(i);
		}
		
		for (int i = 0; i < liCluster.size(); i++) {
			ClusterNode cluster = liCluster.get(i);
			
			PPNodeViz nodeCenter = mdh.getNode(cluster.getIndexCenter());
			
			List<Integer> liIndexClusterNode = cluster.getClusterMember();
			
			for (int j = liIndexClusterNode.size()-1; j >= 0; j--) {
				
				PPNode node = mdh.getNode(liIndexClusterNode.get(j));
				
				if(node.isCarbonExclusiveNode()) {
					
					liIndexNode.remove(liIndexClusterNode.get(j));
					
					int sizeNode = node.getInteractionTypeCount();
										
					boolean added=false;
					
					for (int k = 0; k < sizeNode; k++) {
						
						int interactionIdNode = node.getInteractionType(k);
						
						if(!nodeCenter.containsInteractionID(interactionIdNode)) {
							
							nodeCenter.add(interactionIdNode);
							added=true;
						}
					}
					if(added)
						nodeCenter.realize();
				}
			}
		}
		
		MolDistHistViz mdhSummary = new MolDistHistViz(liIndexNode.size(), mdh.getMolecule());
		
		for (int i = 0; i < liIndexNode.size(); i++) {
			mdhSummary.addNode(mdh.getNode(liIndexNode.get(i)));
		}
		
		for (int i = 0; i < liIndexNode.size(); i++) {
			for (int j = i+1; j < liIndexNode.size(); j++) {
				mdhSummary.setDistHist(i, j, mdh.getDistHist(liIndexNode.get(i), liIndexNode.get(j)));
			}
		}
		
		if(mdh.getDistanceTables() != null){
			List<float [][]> liDistanceArrayNodes = mdh.getDistanceTables();
			
			List<float [][]> liDistanceArrayNodesSummary =  new ArrayList<float[][]>();
			
			for (float[][] arrDistanceNodes : liDistanceArrayNodes) {
				
				float[][] arrDistanceNodesSummary = new float [liIndexNode.size()][liIndexNode.size()];
				
				for (int i = 0; i < liIndexNode.size(); i++) {
					for (int j = 0; j < liIndexNode.size(); j++) {
						arrDistanceNodesSummary[i][j]=arrDistanceNodes[liIndexNode.get(i)][liIndexNode.get(j)];
					}
				}
				
				liDistanceArrayNodesSummary.add(arrDistanceNodesSummary);
			}
			
			mdhSummary.liDistanceTable = liDistanceArrayNodesSummary;
		}
		
		
		mdhSummary.realize();
		
		return mdhSummary;
	}
	

}
