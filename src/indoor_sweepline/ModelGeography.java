package indoor_sweepline;

import java.util.List;
import java.util.Vector;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;


public class ModelGeography
{
    public ModelGeography(DataSet dataSet, LatLon center)
    {
	beamsGeography = new Vector<BeamGeography>();
	
	this.dataSet = dataSet;
	this.center = center;
    
	wayPool = new Vector<Way>();
	wayPoolCount = 0;
	nodePool = new Vector<Node>();
	nodePoolCount = 0;
	
	nodes = null;
	multipolygon = null;
	members = null;
    }
    

    private Vector<BeamGeography> beamsGeography;
    
    private DataSet dataSet;
    private LatLon center;
    
    private Vector<Way> wayPool;
    private int wayPoolCount;
    private Vector<Node> nodePool;
    private int nodePoolCount;
    
    private Vector<Node> nodes;
    private Relation multipolygon;
    private Vector<RelationMember> members;

    
    public void appendNode(Node node)
    {
	nodes.add(node);
    }
    
    
    public DataSet getDataSet()
    {
	return dataSet;
    }
    
    
    public BeamGeography beamAt(int i)
    {
	return beamsGeography.elementAt(i);
    }

    
    public void startGeographyBuild(Vector<Beam> beams, Vector<Strip> strips)
    {
	if (beamsGeography.size() < beams.size())
	    beamsGeography.setSize(beams.size());
	
	double offset = 0;	
	for (int i = 0; i < beams.size(); ++i)
	{
	    if (beamsGeography.elementAt(i) == null)
		beamsGeography.setElementAt(new BeamGeography(dataSet, this), i);
	    beamsGeography.elementAt(i).adjustNodes(new LatLon(center.lat(), addMetersToLon(center, offset)),
		beams.elementAt(i).getBeamParts());
		
	    if (i < strips.size())
		offset += strips.elementAt(i).width;
	}
	
	nodePoolCount = 0;
	wayPoolCount = 0;
	
	members = new Vector<RelationMember>();
	if (multipolygon != null)
	    multipolygon.setMembers(members);
    }
    
    
    public void startWay()
    {
	nodes = new Vector<Node>();
    }
    
    
    public void finishWay(Strip strip, int partIndex, boolean isOuter)
    {
	if (nodes.size() > 0)
	{
	    CorridorPart part = strip.partAt(partIndex);
	    strip.geographyAt(partIndex).appendNodes(part.getType(), part.getSide(),
		nodes.elementAt(nodes.size()-1).getCoor(), nodes.elementAt(0).getCoor(), this);
	    nodes.add(nodes.elementAt(0));
	}
	assignNds(nodes);
	members.add(new RelationMember(isOuter ? "outer" : "inner", wayPool.elementAt(wayPoolCount)));
	++wayPoolCount;
    }
    
    
    public void appendCorridorPart(CorridorPart part, CorridorGeography partGeography, int beamIndex, int partIndex)
    {
	if (nodes.size() > 0)
	    partGeography.appendNodes(part.getType(), part.getSide(),
		nodes.elementAt(nodes.size()-1).getCoor(),
		beamsGeography.elementAt(beamIndex).coorAt(partIndex), this);
    }
    
    
    public void appendUturnNode(Strip strip, int partIndex, int stripIndex, int beamNodeIndex, boolean toTheLeft)
    {
	if (toTheLeft)
	    assignCoor(addMeterOffset(beamsGeography.elementAt(stripIndex + 1).coorAt(beamNodeIndex),
		0, -strip.width / 2.));
	else
	    assignCoor(addMeterOffset(beamsGeography.elementAt(stripIndex).coorAt(beamNodeIndex),
		0, strip.width / 2.));
	
	if (nodes.size() > 0)
	{
	    CorridorPart part = strip.partAt(partIndex);
	    strip.geographyAt(partIndex).appendNodes(part.getType(), part.getSide(),
		nodes.elementAt(nodes.size()-1).getCoor(), nodePool.elementAt(nodePoolCount).getCoor(), this);
	}
	nodes.add(nodePool.elementAt(nodePoolCount));
	++nodePoolCount;
    }
    
    
    public void finishGeographyBuild()
    {
	for (int i = nodePoolCount; i < nodePool.size(); ++i)
	    nodePool.elementAt(i).setDeleted(true);
	nodePool.setSize(nodePoolCount);
	
	for (int i = wayPoolCount; i < wayPool.size(); ++i)
	    wayPool.elementAt(i).setDeleted(true);
	wayPool.setSize(wayPoolCount);
	
	adjustMultipolygonRelation();
    }
    
    
    private static LatLon addMeterOffset(LatLon latLon, double south, double east)
    {
	double scale = Math.cos(latLon.lat() * (Math.PI/180.));
	return new LatLon(latLon.lat() - south *(360./4e7), latLon.lon() + east / scale *(360./4e7));
    }

    
    private static double addMetersToLon(LatLon latLon, double east)
    {
	double scale = Math.cos(latLon.lat() * (Math.PI/180.));
	return latLon.lon() + east / scale *(360./4e7);
    }

    
    private void assignCoor(LatLon latLon)
    {
	if (nodePoolCount < nodePool.size())
	    nodePool.elementAt(nodePoolCount).setCoor(latLon);
	else
	{
	    Node node = new Node(latLon);
	    dataSet.addPrimitive(node);
	    nodePool.add(node);
	}
    }
    
    
    private void assignNds(List<Node> nodes)
    {
	if (wayPoolCount < wayPool.size())
	    wayPool.elementAt(wayPoolCount).setNodes(nodes);
	else
	{
	    Way way = new Way();
	    way.setNodes(nodes);
	    dataSet.addPrimitive(way);
	    wayPool.add(way);
	}
    }
    
    
    private void adjustMultipolygonRelation()
    {
	if (members.size() > 1)
	{
	    if (multipolygon == null)
	    {
		multipolygon = new Relation();
		dataSet.addPrimitive(multipolygon);
	    }
	    multipolygon.setMembers(members);
	}
	else
	{
	    if (multipolygon != null)
	    {
		multipolygon.setDeleted(true);
		multipolygon = null;
	    }
	}
    }
}
