//! A struct that borrows instead of owning, and the two different lifetimes
//! its methods can return.

struct Manifest<'a> {
    source: &'a str,
}

impl<'a> Manifest<'a> {
    fn new(source: &'a str) -> Self {
        Manifest { source }
    }

    /// Elision applies here: there is one input reference, `&self`, so the
    /// result is tied to the borrow of `self` and not to `'a`.
    fn first_line(&self) -> &str {
        self.source.lines().next().unwrap_or("")
    }

    /// Written out. These borrow from the original text, so they stay valid
    /// after the `Manifest` itself is gone.
    fn entity_ids(&self) -> Vec<&'a str> {
        self.source
            .lines()
            .filter_map(|line| line.split_once('='))
            .map(|(key, _)| key.trim())
            .collect()
    }
}

fn main() {
    let document = String::from("lesson-a=12\nlesson-b=13\nmind-map=4");

    let ids = {
        let manifest = Manifest::new(&document);
        println!("first line: {}", manifest.first_line());
        manifest.entity_ids()
    };
    // `manifest` is gone; `document` is not, and that is what the ids borrow.
    println!("ids: {ids:?}");
    println!("ids outlived the manifest: {}", ids.len() == 3);
}
