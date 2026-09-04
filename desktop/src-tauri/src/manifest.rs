//! Manifest and package documents, as the server publishes them.
//!
//! These types mirror the sync protocol's JSON exactly, in `snake_case`. That
//! differs from the command payloads, which are `camelCase`, and the difference
//! is deliberate: the REST surface and the `invoke` surface are separate, and
//! the mapping between them happens once, where a document becomes a stored row
//! or a command response.
//!
//! Nothing here re-derives a digest from these structures. The digest covers
//! the canonical bytes the server produced, so the client hashes the bytes it
//! received and only then parses them; a round trip through these types would
//! reorder keys and lose the very property being verified.

use serde::{Deserialize, Serialize};

use crate::model::EntityType;

#[derive(Debug, Clone, Deserialize)]
pub struct Catalog {
    #[allow(dead_code)]
    pub generated_at: Option<String>,
    #[serde(default)]
    pub tracks: Vec<CatalogTrack>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct CatalogTrack {
    pub track_id: String,
    pub slug: String,
    pub title: String,
    pub content_version: i64,
    #[serde(default)]
    pub lesson_count: i64,
    #[serde(default)]
    pub total_size_bytes: i64,
    #[serde(default)]
    pub updated_at: Option<String>,
}

/// The full manifest for one track: a structural summary the library screen
/// renders, and a flat entity list the download engine consumes.
#[derive(Debug, Clone, Deserialize)]
pub struct TrackManifest {
    pub track_id: String,
    pub slug: String,
    pub content_version: i64,
    pub title: String,
    #[serde(default)]
    pub description: Option<String>,
    #[serde(default)]
    pub icon: Option<String>,
    #[serde(default)]
    pub translations: Vec<ManifestTranslation>,
    #[serde(default)]
    pub modules: Vec<ManifestModule>,
    #[serde(default)]
    pub entities: Vec<ManifestEntity>,
}

impl TrackManifest {
    pub fn entity(&self, entity_id: &str) -> Option<&ManifestEntity> {
        self.entities.iter().find(|e| e.entity_id == entity_id)
    }

    pub fn lessons(&self) -> impl Iterator<Item = &ManifestLesson> {
        self.modules.iter().flat_map(|m| m.lessons.iter())
    }
}

#[derive(Debug, Clone, Deserialize)]
pub struct ManifestTranslation {
    pub locale: String,
    #[serde(default)]
    pub title: Option<String>,
    #[serde(default)]
    pub body: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ManifestModule {
    pub module_id: String,
    pub title: String,
    pub order: i64,
    #[serde(default)]
    pub estimated_minutes: Option<i64>,
    #[serde(default)]
    pub translations: Vec<ManifestTranslation>,
    #[serde(default)]
    pub lessons: Vec<ManifestLesson>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ManifestLesson {
    pub lesson_id: String,
    pub slug: String,
    pub title: String,
    #[serde(default)]
    pub difficulty: Option<String>,
    #[serde(default)]
    pub estimated_minutes: Option<i64>,
    pub order: i64,
    #[serde(default)]
    pub translations: Vec<ManifestTranslation>,
}

/// The five fields the engine needs, and no more. A module download is the
/// entity entries for the lessons the module lists, and nothing else about the
/// engine changes between the three granularities.
#[derive(Debug, Clone, Deserialize)]
pub struct ManifestEntity {
    pub entity_type: EntityType,
    pub entity_id: String,
    pub content_version: i64,
    pub sha256: String,
    pub size_bytes: i64,
}

/// A package document, discriminated by the `entity_type` the body carries.
#[derive(Debug, Clone, Deserialize)]
#[serde(tag = "entity_type")]
pub enum Package {
    #[serde(rename = "LESSON")]
    Lesson(LessonPackage),
    #[serde(rename = "MIND_MAP")]
    MindMap(MindMapPackage),
}

impl Package {
    pub fn entity_id(&self) -> &str {
        match self {
            Package::Lesson(p) => &p.entity_id,
            Package::MindMap(p) => &p.entity_id,
        }
    }

    pub fn content_version(&self) -> i64 {
        match self {
            Package::Lesson(p) => p.content_version,
            Package::MindMap(p) => p.content_version,
        }
    }
}

#[derive(Debug, Clone, Deserialize)]
pub struct LessonPackage {
    pub entity_id: String,
    pub content_version: i64,
    pub module_id: String,
    pub slug: String,
    pub title: String,
    pub body_markdown: String,
    #[serde(default)]
    pub difficulty: Option<String>,
    #[serde(default)]
    pub estimated_minutes: Option<i64>,
    #[serde(default)]
    pub order: i64,
    #[serde(default)]
    pub code_examples: Vec<PackageCodeExample>,
    #[serde(default)]
    pub translations: Vec<PackageTranslation>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct PackageCodeExample {
    #[serde(default)]
    pub caption: Option<String>,
    pub code: String,
    pub language: String,
    #[serde(default)]
    pub order: i64,
}

#[derive(Debug, Clone, Deserialize)]
pub struct PackageTranslation {
    pub locale: String,
    #[serde(default)]
    pub title: Option<String>,
    #[serde(default)]
    pub body_markdown: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct MindMapPackage {
    pub entity_id: String,
    pub content_version: i64,
    pub track_id: String,
    /// Stored verbatim. The node tree's shape is owned by the read API and the
    /// package transports it unchanged, so re-modelling it here would create a
    /// second definition that could drift from the first.
    pub root: serde_json::Value,
}

/// The shared error envelope, as it arrives in a non-2xx response body.
#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct ApiError {
    pub code: String,
    #[serde(default)]
    pub message: Option<String>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_a_track_manifest_with_both_readers() {
        let json = r##"{
            "track_id": "018f3a01-2b7c-7a41-8f10-5c9d3e77aa10",
            "slug": "angular-path",
            "content_version": 47,
            "title": "The Angular Path",
            "description": "Signals, routing and the modern component model.",
            "icon": "angular",
            "translations": [{ "locale": "tr", "title": "Angular Yolu", "body": "Sinyaller." }],
            "modules": [{
                "module_id": "018f3a02-4411-7f60-9c22-77b0a1e4cc90",
                "title": "Signals and reactivity",
                "order": 1,
                "estimated_minutes": 90,
                "translations": [{ "locale": "tr", "title": "Reaktivite Temelleri" }],
                "lessons": [{
                    "lesson_id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
                    "slug": "signals-and-reactivity",
                    "title": "Introduction to signals",
                    "difficulty": "INTERMEDIATE",
                    "estimated_minutes": 25,
                    "order": 1,
                    "translations": [{ "locale": "tr", "title": "Sinyallere giris" }]
                }]
            }],
            "entities": [
                { "entity_type": "LESSON", "entity_id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
                  "content_version": 12, "sha256": "9f2c", "size_bytes": 41233 },
                { "entity_type": "MIND_MAP", "entity_id": "018f3b40-2a83-7e19-8f77-1c5b9e04a6d2",
                  "content_version": 5, "sha256": "1a3c", "size_bytes": 8117 }
            ]
        }"##;

        let manifest: TrackManifest = serde_json::from_str(json).expect("parse manifest");
        assert_eq!(manifest.content_version, 47);
        assert_eq!(manifest.lessons().count(), 1);
        assert_eq!(manifest.entities.len(), 2);
        assert_eq!(manifest.entities[1].entity_type, EntityType::MindMap);
    }

    #[test]
    fn a_package_is_discriminated_by_its_entity_type() {
        let lesson = r##"{
            "entity_type": "LESSON",
            "entity_id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
            "content_version": 12,
            "module_id": "018f3a02-4411-7f60-9c22-77b0a1e4cc90",
            "slug": "signals-basics",
            "title": "Introduction to signals",
            "body_markdown": "# Signals\n",
            "difficulty": "INTERMEDIATE",
            "estimated_minutes": 25,
            "order": 3,
            "code_examples": [{ "caption": "A writable signal", "code": "const c = signal(0);", "language": "typescript", "order": 1 }],
            "translations": [{ "locale": "tr", "title": "Sinyallere giris", "body_markdown": "# Sinyaller\n" }]
        }"##;
        match serde_json::from_str::<Package>(lesson).expect("parse lesson package") {
            Package::Lesson(p) => {
                assert_eq!(p.code_examples.len(), 1);
                assert_eq!(p.translations[0].locale, "tr");
            }
            other => panic!("expected a lesson package, got {other:?}"),
        }

        let mind_map = r##"{
            "entity_type": "MIND_MAP",
            "entity_id": "018f3b40-2a83-7e19-8f77-1c5b9e04a6d2",
            "content_version": 5,
            "track_id": "018f3a01-2b7c-7a41-8f10-5c9d3e77aa10",
            "root": { "id": "n1", "label": "The Angular Path", "children": [] }
        }"##;
        match serde_json::from_str::<Package>(mind_map).expect("parse mind map package") {
            Package::MindMap(p) => assert_eq!(p.root["id"], "n1"),
            other => panic!("expected a mind map package, got {other:?}"),
        }
    }

    #[test]
    fn a_translation_absent_from_a_package_is_simply_absent() {
        // A missing locale is not an error and not an empty entry: the client
        // falls back to English and marks the lesson untranslated.
        let json = r##"{
            "entity_type": "LESSON",
            "entity_id": "018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70",
            "content_version": 1,
            "module_id": "018f3a02-4411-7f60-9c22-77b0a1e4cc90",
            "slug": "s", "title": "t", "body_markdown": "b"
        }"##;
        match serde_json::from_str::<Package>(json).expect("parse") {
            Package::Lesson(p) => assert!(p.translations.is_empty()),
            other => panic!("expected a lesson package, got {other:?}"),
        }
    }
}
