use holda::StringHolda;

#[derive(StringHolda)]
pub struct CheckedOutBranch {
    inner: String,
}
