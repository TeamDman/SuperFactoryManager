#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum SourceCatalogCategory {
    Test,
    GameTest,
    Puppet,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum SourceCatalogAction {
    List,
    Show { id: String },
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SourceCatalogQuery {
    pub category: SourceCatalogCategory,
    pub action: SourceCatalogAction,
}
