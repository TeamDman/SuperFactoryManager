#![feature(try_blocks)]

mod preprocess;

use core::f32;
use itertools::Itertools;
use preprocess::get_data;
use preprocess::DerivedEmc;
use preprocess::HasEmc;
use preprocess::Item;
use preprocess::ProcessedData;
use rayon::iter::ParallelBridge;
use rayon::iter::ParallelIterator;
use std::fs::{self};
use std::path::PathBuf;

fn update_derived_emc<F, E>(data: ProcessedData, item_filter: F, epoch_filter: E) -> ProcessedData
where
    F: Fn(&Item) -> bool + Sync,
    E: Fn(i32) -> bool + Sync,
{
    let start = std::time::Instant::now();
    let mut items = data.items;
    let mut recipes = data.recipes;
    let mut epoch = 0;
    let mut changed = true;
    loop {
        // we want to update the emc on all the recipes
        // we also want to do this before returning
        recipes.iter_mut().par_bridge().for_each(|recipe| {
            for ingredient in recipe.inputs.iter_mut().chain(recipe.outputs.iter_mut()) {
                if let Some(item) = items.get(&ingredient.ingredient_id) {
                    if ingredient.emc.is_none() {
                        ingredient.emc = item.emc;
                    }
                    if let Some(derived_emc) = item.best_acquire_emc.as_ref() {
                        ingredient.best_emc_acquire = Some(derived_emc.clone());
                    }
                    if let Some(derived_emc) = item.best_burn_emc.as_ref() {
                        ingredient.best_emc_burn = Some(derived_emc.clone());
                    }
                }
            }
        });

        if !epoch_filter(epoch) {
            println!("Stopping after {} epochs", epoch);
            break;
        } else {
            if !changed {
                println!("No changes in epoch {}", epoch - 1);
                break;
            }
            println!("Starting epoch {}", epoch);
            epoch += 1;
        }

        changed = items
            .values_mut()
            .par_bridge()
            .filter(|item| item_filter(item))
            .map(|item| {
                let mut changed = false;
                if item.get_burn_emc().is_none() {
                    // find a recipe that consumes this item and gives us something we can burn
                    for recipe in recipes.iter() {
                        let mut inputs_our_item = false;
                        let mut inputs_lacking_emc = false;
                        let mut input_amount = 0;
                        for ingredient in recipe.inputs.iter() {
                            if ingredient.ingredient_id == item.id {
                                input_amount += ingredient.ingredient_amount;
                                inputs_our_item = true;
                            } else if ingredient.get_acquire_emc().is_none() {
                                inputs_lacking_emc = true;
                            }
                        }
                        if inputs_our_item && !inputs_lacking_emc {
                            let output_emc = recipe.get_output_emc();
                            let input_emc = recipe.get_input_emc();
                            let diff = output_emc - input_emc;
                            if diff > 0.0 {
                                let emc_for_item = diff / input_amount as f32;
                                let current_best =
                                    item.best_burn_emc.as_ref().map(|x| x.emc).unwrap_or(0.);

                                // is this a better burn
                                if current_best < emc_for_item {
                                    // do not proceed if the path already contains this recipe
                                    if item.best_burn_emc.as_ref().map(|x| x.path.contains(&recipe.recipe_id)).unwrap_or(false) {
                                        continue;
                                    }

                                    // add this recipe to the path
                                    let old = item.best_burn_emc.take();
                                    let mut path = old.map(|x| x.path).unwrap_or_default();
                                    path.push(recipe.recipe_id.to_owned());

                                    // update the best burn emc
                                    item.best_burn_emc = Some(DerivedEmc {
                                        emc: emc_for_item,
                                        path,
                                    });
                                    // println!(
                                    //     "Found derived burn emc for {:#?} using recipe {:#?}",
                                    //     item, recipe
                                    // );
                                    changed = true;
                                }
                            }
                        }
                    }
                }
                if item.get_acquire_emc().is_none() {
                    // find a recipe that produces this item using only items that can be acquired
                    for recipe in recipes.iter() {
                        let mut outputs_our_item = false;
                        let mut inputs_lacking_emc = false;
                        let mut output_amount = 0;
                        for ingredient in recipe.outputs.iter() {
                            if ingredient.ingredient_id == item.id {
                                output_amount += ingredient.ingredient_amount;
                                outputs_our_item = true;
                            }
                        }
                        for ingredient in recipe.inputs.iter() {
                            if ingredient.get_acquire_emc().is_none() {
                                inputs_lacking_emc = true;
                            }
                        }
                        if outputs_our_item && !inputs_lacking_emc {
                            let input_emc = recipe.get_input_emc();
                            let emc_for_item = input_emc / output_amount as f32;
                            if item
                                .best_acquire_emc
                                .as_ref()
                                .map(|x| x.emc)
                                .unwrap_or(f32::MAX)
                                > emc_for_item
                            {
                                item.best_acquire_emc = Some(DerivedEmc {
                                    emc: emc_for_item,
                                    path: vec![recipe.recipe_id.to_owned()],
                                });
                                // println!(
                                //     "Found derived acquire emc for {:#?} using recipe {:#?}",
                                //     item, recipe
                                // );
                                changed = true;
                            }
                        }
                    }
                }
                changed
            })
            .reduce(|| false, |acc, x| acc || x);
    }

    println!(
        "Updated derived emc for all recipes in {} seconds",
        start.elapsed().as_secs()
    );
    ProcessedData { items, recipes }
}

fn main() {
    let output_dir = PathBuf::from("outputs");

    // remove output dir if exists
    if output_dir.exists() {
        fs::remove_dir_all(&output_dir).expect("Unable to remove output directory");
    }
    fs::create_dir_all(&output_dir).expect("Unable to create output directory");

    let data = get_data();
    println!(
        "Loaded {} recipes and {} items",
        data.recipes.len(),
        data.items.len()
    );

    let data = update_derived_emc(
        data,
        |item| item.id.starts_with("minecraft:"),
        // |item| true,
        |epoch| epoch < 6,
        // |epoch| epoch < 2,
    );
    // write items as items.json
    let items_path = output_dir.join("items.json");
    let items_json = serde_json::to_string_pretty(&data.items).unwrap();
    fs::write(&items_path, items_json).expect("Unable to write items.json");

    // write recipes as recipes.json
    let recipes_path = output_dir.join("recipes.json");
    let recipes_json = serde_json::to_string_pretty(&data.recipes).unwrap();
    fs::write(&recipes_path, recipes_json).expect("Unable to write recipes.json");

    let gains = data
        .items
        .values()
        .filter(|item| item.get_acquire_emc().is_some() && item.get_burn_emc().is_some())
        .filter_map(|item| {
            let acquire = item.get_acquire_emc().unwrap();
            let burn = item.get_burn_emc().unwrap();
            if acquire < burn {
                Some((item, burn - acquire))
            } else {
                None
            }
        })
        .sorted_by_key(|x| (x.1 * 1000.0) as i32)
        .map(|x| x.0)
        .collect::<Vec<_>>();
    // write gains as gains.json
    let gains_path = output_dir.join("gains.json");
    let gains_json = serde_json::to_string_pretty(&gains).unwrap();
    fs::write(&gains_path, gains_json).expect("Unable to write gains.json");
}

#[cfg(test)]
mod tests {
    use crate::preprocess::get_data;
    use crate::preprocess::HasEmc;
    use crate::update_derived_emc;
    use rayon::iter::IntoParallelRefIterator;
    use rayon::iter::ParallelIterator;

    #[test]
    fn find_blaze_powder_recipes() {
        let data = get_data();

        let found = data
            .recipes
            .par_iter()
            .filter(|recipe| {
                recipe
                    .outputs
                    .iter()
                    .all(|ing| ing.ingredient_id == "minecraft:blaze_powder")
                    && recipe
                        .inputs
                        .iter()
                        .all(|ing| ing.ingredient_id == "minecraft:blaze_rod")
            })
            .collect::<Vec<_>>();
        assert!(!found.is_empty());
        for recipe in found {
            println!("{:#?}", recipe);
            println!();
        }
    }

    #[test]
    fn find_blaze_powder_emc() {
        // blaze powder has no emc
        // blaze powder plus ender pearl gives eye of ender which has emc
        let data = get_data();

        let blaze_powder = data.items.get("minecraft:blaze_powder").unwrap();
        assert_eq!(blaze_powder.emc, None);

        let data = update_derived_emc(
            data,
            |item| item.id == "minecraft:blaze_powder",
            |epoch| epoch < 2,
        );

        let blaze_powder = data.items.get("minecraft:blaze_powder").unwrap();
        dbg!(blaze_powder);
        assert_eq!(blaze_powder.get_burn_emc().unwrap(), 768.0);
    }

    #[test]
    fn find_blaze_powder_loop() {
        // 1 blaze rod - mechanical squeezer -> 5 blaze powder (no emc) - crafting -> ender pearl (has emc)
        let data = get_data();
        let data = update_derived_emc(
            data,
            |item| item.id == "minecraft:blaze_powder",
            |epoch| epoch < 2,
        );

        for recipe in data.recipes {
            if recipe.has_non_emc_input() || recipe.has_non_emc_output() {
                continue;
            }
            if recipe
                .inputs
                .iter()
                .any(|ing| ing.ingredient_id == "minecraft:blaze_powder")
            {
                if recipe.get_output_emc() > recipe.get_input_emc() {
                    println!("{:#?}", recipe);
                }
            }
        }
    }
}
