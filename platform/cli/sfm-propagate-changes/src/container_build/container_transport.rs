use facet::Facet;
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq, Facet)]
#[repr(u8)]
pub enum ContainerTransport {
    #[default]
    NativeApi,
    Command,
}
