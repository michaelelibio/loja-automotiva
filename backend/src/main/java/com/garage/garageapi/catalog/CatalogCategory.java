package com.garage.garageapi.catalog;
import jakarta.persistence.*;
@Entity @Table(name="catalog_categories")
public class CatalogCategory {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=120) private String name;
    @Column(nullable=false,length=120,updatable=false) private String slug;
    @Column(nullable=false,unique=true,length=250,updatable=false) private String path;
    @Column(length=1500) private String description;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="parent_id",updatable=false) private CatalogCategory parent;
    @Column(nullable=false) private int position;
    @Column(nullable=false) private boolean active;
    protected CatalogCategory() { }
    public CatalogCategory(String name,String slug,CatalogCategory parent,String description,int position,boolean active) {
        this.slug=slug;this.parent=parent;this.path=parent==null?slug:parent.path+"/"+slug;
        update(name,description,position,active);
    }
    public void update(String name,String description,int position,boolean active) {this.name=name.trim();this.description=description;this.position=position;this.active=active;}
    public boolean visible(){return active && (parent==null || parent.active);}
    public Long getId(){return id;} public String getName(){return name;} public String getSlug(){return slug;}
    public String getPath(){return path;} public String getDescription(){return description;}
    public CatalogCategory getParent(){return parent;} public int getPosition(){return position;} public boolean isActive(){return active;}
}
