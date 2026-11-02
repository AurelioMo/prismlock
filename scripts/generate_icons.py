from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / 'app/src/main/res'


def main():
    logo = Image.open(ROOT / 'docs/branding/logo.png').convert('RGBA')
    for density, size in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
        directory = RESOURCES / ('mipmap-' + density)
        directory.mkdir(parents=True, exist_ok=True)
        logo.resize((size, size), Image.Resampling.LANCZOS).save(directory / 'ic_launcher.png', optimize=True)
    foreground = Image.new('RGBA', (432, 432))
    foreground.alpha_composite(logo.resize((384, 384), Image.Resampling.LANCZOS), (24, 24))
    foreground.save(RESOURCES / 'drawable-nodpi/ic_launcher_foreground.png', optimize=True)


if __name__ == '__main__':
    main()
