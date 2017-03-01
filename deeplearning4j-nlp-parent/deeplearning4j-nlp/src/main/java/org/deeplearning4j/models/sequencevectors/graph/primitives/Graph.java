package org.deeplearning4j.models.sequencevectors.graph.primitives;

import org.deeplearning4j.models.sequencevectors.graph.exception.NoEdgesException;
import org.deeplearning4j.models.sequencevectors.graph.vertex.VertexFactory;
import org.deeplearning4j.models.sequencevectors.sequence.SequenceElement;

import java.lang.reflect.Array;
import java.util.*;

/** Graph, where all edges and vertices are stored in-memory.<br>
 * Internally, this is a directed graph with adjacency list representation; however, if undirected edges
 * are added, these edges are duplicated internally to allow for fast lookup.<br>
 * Depending on the value of {@code allowMultipleEdges}, this graph implementation may or may not allow
 * multiple edges between any two adjacent nodes. If multiple edges are required (such that two or more distinct edges
 * between vertices X and Y exist simultaneously) then {@code allowMultipleEdges} should be set to {@code true}.<br>
 * As per {@link IGraph}, this graph representation can have arbitrary objects attached<br>
 * Vertices are initialized either directly via list, or via a {@link VertexFactory}. Edges are added using one of the
 * addEdge methods.
 * @param <V> Type parameter for vertices (type of objects attached to each vertex)
 * @param <E> Type parameter for edges (type of objects attached to each edge)
 * @author Alex Black
 */
public class Graph<V extends SequenceElement, E extends Number> implements IGraph<V,E> {
    private boolean allowMultipleEdges;
    private List<List<Edge<E>>> edges;  //edge[i].get(j).to = k, then edge from i -> k
    private List<Vertex<V>> vertices;


    public Graph(int numVertices, VertexFactory<V> vertexFactory){
        this(numVertices,false,vertexFactory);
    }

    @SuppressWarnings("unchecked")
    public Graph(int numVertices, boolean allowMultipleEdges, VertexFactory<V> vertexFactory){
        if(numVertices <= 0 ) throw new IllegalArgumentException();
        this.allowMultipleEdges = allowMultipleEdges;

        vertices = new ArrayList<>(numVertices);
        for( int i=0; i<numVertices; i++ ) vertices.add(vertexFactory.create(i));

        edges = new ArrayList<>(numVertices);
        for (int i = 0; i < numVertices; i++)
            edges.add(new ArrayList<Edge<E>>());
    }


    @SuppressWarnings("unchecked")
    public Graph(List<Vertex<V>> vertices, boolean allowMultipleEdges){
        this.vertices = new ArrayList<>(vertices);
        this.allowMultipleEdges = allowMultipleEdges;

        edges = new ArrayList<>(vertices.size());
        for (int i = 0; i < vertices.size(); i++)
            edges.add(new ArrayList<Edge<E>>());
    }

    @SuppressWarnings("unchecked")
    public Graph(Collection<V> elements, boolean allowMultipleEdges) {
        this.vertices = new ArrayList<>();
        this.allowMultipleEdges = allowMultipleEdges;
        int idx = 0;
        for (V element: elements) {
            Vertex<V> vertex = new Vertex<>(idx, element);
            vertices.add(vertex);
            idx++;
        }
        edges = new ArrayList<>(vertices.size());
        for (int i = 0; i < vertices.size(); i++)
            edges.add(new ArrayList<Edge<E>>());
    }

    public Graph(List<Vertex<V>> vertices){
        this(vertices,false);
    }

    public Graph() {
    }

    public void addVertex(Vertex<V> vertex, Edge<E> edge) {
        vertices.add(vertex);
        edges.add(new ArrayList<Edge<E>>());
        this.addEdge(edge);
    }

    public void addVertex(Vertex<V> vertex, Collection<Edge<E>> edges ) {
        vertices.add(vertex);
        this.edges.add(new ArrayList<Edge<E>>());
        for (Edge<E> edge: edges) {
            this.addEdge(edge);
        }
    }

    @Override
    public int numVertices() {
        return vertices.size();
    }

    @Override
    public Vertex<V> getVertex(int idx) {
        if(idx < 0 || idx >= vertices.size() ) throw new IllegalArgumentException("Invalid index: " + idx);
        return vertices.get(idx);
    }

    @Override
    public List<Vertex<V>> getVertices(int[] indexes) {
        List<Vertex<V>> out = new ArrayList<>(indexes.length);
        for(int i : indexes) out.add(getVertex(i));
        return out;
    }

    @Override
    public List<Vertex<V>> getVertices(int from, int to) {
        if(to < from || from < 0 || to >= vertices.size())
            throw new IllegalArgumentException("Invalid range: from="+from + ", to="+to);
        List<Vertex<V>> out = new ArrayList<>(to-from+1);
        for(int i=from; i<=to; i++ ) out.add(getVertex(i));
        return out;
    }

    @Override
    public void addEdge(Edge<E> edge) {
        if(edge.getFrom() < 0 || edge.getTo() >= vertices.size() )
            throw new IllegalArgumentException("Invalid edge: " + edge + ", from/to indexes out of range");

        List<Edge<E>> fromList = edges.get(edge.getFrom());
        if(fromList == null){
            fromList = new ArrayList<>();
            edges.set(edge.getFrom(), fromList);
        }
        addEdgeHelper(edge,fromList);

        if(edge.isDirected()) return;

        //Add other way too (to allow easy lookup for undirected edges)
        List<Edge<E>> toList = edges.get(edge.getTo());
        if(toList == null){
            toList = new ArrayList<>();
            edges.set(edge.getTo(), toList);
        }
        addEdgeHelper(edge,toList);
    }

    /**
     * Convenience method for adding an edge (directed or undirected) to graph
     *
     * @param from
     * @param to
     * @param value
     * @param directed
     */
    @Override
    public void addEdge(int from, int to, E value, boolean directed) {
        addEdge(new Edge<>(from, to, value, directed));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Edge<E>> getEdgesOut(int vertex) {
        if(edges.get(vertex) == null ) return Collections.emptyList();
        return edges.get(vertex);
    }

    @Override
    public int getVertexDegree(int vertex){
        if(edges.get(vertex) == null) return 0;
        return edges.get(vertex).size();
    }

    @Override
    public Vertex<V> getRandomConnectedVertex(int vertex, Random rng) throws NoEdgesException {
        if(vertex < 0 || vertex >= vertices.size() ) throw new IllegalArgumentException("Invalid vertex index: " + vertex);
        if(edges.get(vertex) == null || edges.get(vertex).isEmpty())
            throw new NoEdgesException("Cannot generate random connected vertex: vertex " + vertex + " has no outgoing/undirected edges");
        int connectedVertexNum = rng.nextInt(edges.get(vertex).size());
        Edge<E> edge = edges.get(vertex).get(connectedVertexNum);
        if(edge.getFrom() == vertex ) return vertices.get(edge.getTo());    //directed or undirected, vertex -> x
        else return vertices.get(edge.getFrom());   //Undirected edge, x -> vertex
    }

    @Override
    public List<Vertex<V>> getConnectedVertices(int vertex) {
        if(vertex < 0 || vertex >= vertices.size()) throw new IllegalArgumentException("Invalid vertex index: " + vertex);

        if(edges.get(vertex) == null) return Collections.emptyList();
        List<Vertex<V>> list = new ArrayList<>(edges.get(vertex).size());
        for(Edge<E> edge : edges.get(vertex)){
            list.add(vertices.get(edge.getTo()));
        }
        return list;
    }

    @Override
    public int[] getConnectedVertexIndices(int vertex){
        int[] out = new int[(edges.get(vertex) == null ? 0 : edges.get(vertex).size())];
        if(out.length == 0 ) return out;
        for(int i=0; i<out.length; i++ ){
            Edge<E> e = edges.get(vertex).get(i);
            out[i] = (e.getFrom() == vertex ? e.getTo() : e.getFrom() );
        }
        return out;
    }

    private void addEdgeHelper(Edge<E> edge, List<Edge<E>> list ){
        if(!allowMultipleEdges){
            //Check to avoid multiple edges
            boolean duplicate = false;

            if(edge.isDirected()){
                for(Edge<E> e : list ){
                    if(e.getTo() == edge.getTo()){
                        duplicate = true;
                        break;
                    }
                }
            } else {
                for(Edge<E> e : list ){
                    if((e.getFrom() == edge.getFrom() && e.getTo() == edge.getTo())
                            || (e.getTo() == edge.getFrom() && e.getFrom() == edge.getTo())){
                        duplicate = true;
                        break;
                    }
                }
            }

            if(!duplicate){
                list.add(edge);
            }
        } else {
            //allow multiple/duplicate edges
            list.add(edge);
        }
    }


    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append("Graph {");
        sb.append("\nVertices {");
        for(Vertex<V> v : vertices){
            sb.append("\n\t").append(v);
        }
        sb.append("\n}");
        sb.append("\nEdges {");
        for( int i=0; i<edges.size(); i++ ){
            sb.append("\n\t");
            if(edges.get(i) == null) continue;
            sb.append(i).append(":");
            for(Edge<E> e : edges.get(i)){
                sb.append(" ").append(e);
            }
        }
        sb.append("\n}");
        sb.append("\n}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o){
        if(!(o instanceof Graph)) return false;
        Graph g = (Graph)o;
        if(allowMultipleEdges != g.allowMultipleEdges) return false;
        if(edges.size() != g.edges.size()) return false;
        if(vertices.size() != g.vertices.size()) return false;
        for( int i=0; i<edges.size(); i++ ){
            if(!edges.get(i).equals(g.edges.get(i))) return false;
        }
        return vertices.equals(g.vertices);
    }

    @Override
    public int hashCode() {
        int result = 23;
        result = 31 * result + (allowMultipleEdges? 1 : 0);
        result = 31 * result + edges.hashCode();
        result = 31 * result + vertices.hashCode();
        return result;
    }
}
